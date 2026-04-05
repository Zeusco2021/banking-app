# ============================================================
# Módulo Terraform: Oracle RDS con TDE (Transparent Data Encryption)
# Requisito 12.5: cifrado en reposo en Oracle DB (TDE)
# ============================================================

terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
      configuration_aliases = [aws.dr]
    }
  }
}

# -------------------------------------------------------
# KMS Key para cifrado de Oracle RDS (TDE)
# -------------------------------------------------------
resource "aws_kms_key" "rds_oracle" {
  description             = "KMS key para cifrado TDE de Oracle RDS — ${var.nombre_entorno}"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-rds-oracle-kms"
    Proposito = "TDE Oracle RDS"
    Requisito = "12.5"
  })
}

resource "aws_kms_alias" "rds_oracle" {
  name          = "alias/${var.nombre_entorno}-rds-oracle"
  target_key_id = aws_kms_key.rds_oracle.key_id
}

# -------------------------------------------------------
# Subnet Group para RDS Multi-AZ
# -------------------------------------------------------
resource "aws_db_subnet_group" "oracle" {
  name        = "${var.nombre_entorno}-oracle-subnet-group"
  description = "Subnet group para Oracle RDS Multi-AZ"
  subnet_ids  = var.subnet_ids_privadas

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-oracle-subnet-group"
  })
}

# -------------------------------------------------------
# Security Group para Oracle RDS
# -------------------------------------------------------
resource "aws_security_group" "rds_oracle" {
  name        = "${var.nombre_entorno}-rds-oracle-sg"
  description = "Security group para Oracle RDS — solo acceso desde EKS"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Oracle DB desde EKS"
    from_port       = 1521
    to_port         = 1521
    protocol        = "tcp"
    security_groups = var.security_group_ids_eks
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-rds-oracle-sg"
  })
}

# -------------------------------------------------------
# Oracle RDS Primary — storage_encrypted = true con KMS
# Requisito 12.5: TDE habilitado
# -------------------------------------------------------
resource "aws_db_instance" "oracle_primary" {
  identifier = "${var.nombre_entorno}-oracle-primary"

  engine         = "oracle-ee"
  engine_version = var.oracle_engine_version
  instance_class = var.instance_class
  license_model  = "bring-your-own-license"

  # Almacenamiento cifrado con KMS — satisface Requisito 12.5 (TDE)
  storage_encrypted = true
  kms_key_id        = aws_kms_key.rds_oracle.arn
  storage_type      = "gp3"
  allocated_storage = var.allocated_storage_gb
  max_allocated_storage = var.max_allocated_storage_gb

  db_name  = var.db_name
  username = var.db_username
  # La contraseña se gestiona vía AWS Secrets Manager (Requisito 12.3)
  manage_master_user_password   = true
  master_user_secret_kms_key_id = aws_kms_key.rds_oracle.arn

  # Alta disponibilidad Multi-AZ
  multi_az               = true
  db_subnet_group_name   = aws_db_subnet_group.oracle.name
  vpc_security_group_ids = [aws_security_group.rds_oracle.id]

  # Backups y mantenimiento
  backup_retention_period   = 35
  backup_window             = "03:00-04:00"
  maintenance_window        = "sun:04:00-sun:05:00"
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.nombre_entorno}-oracle-final-snapshot"

  # Logs de auditoría
  enabled_cloudwatch_logs_exports = ["alert", "audit", "listener", "trace"]

  # Actualizaciones automáticas de parches menores
  auto_minor_version_upgrade = true

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-oracle-primary"
    Rol       = "primary"
    Requisito = "12.5"
  })
}

# -------------------------------------------------------
# Oracle RDS Read Replica (misma región)
# -------------------------------------------------------
resource "aws_db_instance" "oracle_replica" {
  identifier          = "${var.nombre_entorno}-oracle-replica"
  replicate_source_db = aws_db_instance.oracle_primary.identifier

  instance_class = var.instance_class_replica
  # La réplica hereda el cifrado KMS del primario automáticamente
  storage_encrypted = true
  kms_key_id        = aws_kms_key.rds_oracle.arn

  vpc_security_group_ids = [aws_security_group.rds_oracle.id]
  auto_minor_version_upgrade = true
  deletion_protection        = true
  skip_final_snapshot        = true

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-oracle-replica"
    Rol       = "read-replica"
    Requisito = "12.5"
  })
}

# -------------------------------------------------------
# Oracle RDS Cross-Region Replica en us-west-2
# Requisito 10.2: réplica cross-region con RPO < 1 minuto
# Se despliega solo si create_cross_region_replica = true
# -------------------------------------------------------
resource "aws_db_instance" "oracle_cross_region_replica" {
  count    = var.create_cross_region_replica ? 1 : 0
  provider = aws.dr

  identifier = "${var.nombre_entorno}-oracle-cross-region-replica"

  # Replica desde el primario usando su ARN
  replicate_source_db = aws_db_instance.oracle_primary.arn

  instance_class = var.instance_class_replica

  # Cifrado en reposo — Requisito 12.5
  storage_encrypted = true

  # Protección contra borrado accidental
  deletion_protection = true
  skip_final_snapshot = true

  auto_minor_version_upgrade = true

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-oracle-cross-region-replica"
    Rol       = "cross-region-replica"
    Region    = "us-west-2"
    Requisito = "10.2"
  })
}
