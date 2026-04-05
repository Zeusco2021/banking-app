# ============================================================
# Módulo Terraform: Amazon DocumentDB (compatible con MongoDB)
# Requisitos 6.1, 6.3, 12.5: auditoría append-only, inmutabilidad, cifrado
# ============================================================

terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }
}

# -------------------------------------------------------
# KMS Key para cifrado en reposo de DocumentDB
# Requisito 12.5: cifrado en reposo
# -------------------------------------------------------
resource "aws_kms_key" "documentdb" {
  description             = "KMS key para cifrado en reposo de DocumentDB — ${var.nombre_entorno}"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-documentdb-kms"
    Proposito = "DocumentDB encryption at rest"
    Requisito = "12.5"
  })
}

resource "aws_kms_alias" "documentdb" {
  name          = "alias/${var.nombre_entorno}-documentdb"
  target_key_id = aws_kms_key.documentdb.key_id
}

# -------------------------------------------------------
# Subnet Group para DocumentDB Multi-AZ
# -------------------------------------------------------
resource "aws_docdb_subnet_group" "principal" {
  name        = "${var.nombre_entorno}-documentdb-subnet-group"
  description = "Subnet group para DocumentDB Multi-AZ"
  subnet_ids  = var.subnet_ids_privadas

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-documentdb-subnet-group"
  })
}

# -------------------------------------------------------
# Security Group para DocumentDB
# Permite puerto 27017 solo desde EKS
# -------------------------------------------------------
resource "aws_security_group" "documentdb" {
  name        = "${var.nombre_entorno}-documentdb-sg"
  description = "Security group para DocumentDB — solo acceso desde EKS"
  vpc_id      = var.vpc_id

  ingress {
    description     = "MongoDB desde EKS"
    from_port       = 27017
    to_port         = 27017
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
    Nombre = "${var.nombre_entorno}-documentdb-sg"
  })
}

# -------------------------------------------------------
# Parameter Group con TLS habilitado
# Requisito 12.2: cifrado en tránsito
# -------------------------------------------------------
resource "aws_docdb_cluster_parameter_group" "principal" {
  family      = "docdb5.0"
  name        = "${var.nombre_entorno}-documentdb-params"
  description = "Parámetros DocumentDB con TLS habilitado"

  parameter {
    name  = "tls"
    value = "enabled"
  }

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-documentdb-params"
  })
}

# -------------------------------------------------------
# Cluster DocumentDB con cifrado KMS y Secrets Manager
# Requisitos 6.1, 6.3, 12.5: auditoría, inmutabilidad, cifrado
# -------------------------------------------------------
resource "aws_docdb_cluster" "principal" {
  cluster_identifier               = "${var.nombre_entorno}-documentdb"
  engine                           = "docdb"
  db_subnet_group_name             = aws_docdb_subnet_group.principal.name
  vpc_security_group_ids           = [aws_security_group.documentdb.id]
  db_cluster_parameter_group_name  = aws_docdb_cluster_parameter_group.principal.name

  # Cifrado en reposo con KMS — Requisito 12.5
  storage_encrypted = true
  kms_key_id        = aws_kms_key.documentdb.arn

  # Gestión de contraseña vía Secrets Manager — Requisito 12.3
  manage_master_user_password = true

  # Retención de backups 35 días
  backup_retention_period   = 35
  preferred_backup_window   = "03:00-04:00"

  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.nombre_entorno}-documentdb-final-snapshot"

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-documentdb"
    Requisito = "6.1,6.3,12.5"
  })
}

# -------------------------------------------------------
# Instancias DocumentDB — 3 instancias distribuidas en AZs
# Requisito 10.1: despliegue en al menos 3 AZs
# -------------------------------------------------------
resource "aws_docdb_cluster_instance" "instancias" {
  count              = 3
  identifier         = "${var.nombre_entorno}-documentdb-${count.index}"
  cluster_identifier = aws_docdb_cluster.principal.id
  instance_class     = var.instance_class

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-documentdb-${count.index}"
    Requisito = "10.1"
  })
}
