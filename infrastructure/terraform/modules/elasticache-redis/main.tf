# ============================================================
# Módulo Terraform: ElastiCache Redis con cifrado en reposo
# Requisito 12.5: at_rest_encryption_enabled = true
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
# KMS Key para cifrado en reposo de Redis
# -------------------------------------------------------
resource "aws_kms_key" "redis" {
  description             = "KMS key para cifrado en reposo de ElastiCache Redis — ${var.nombre_entorno}"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-redis-kms"
    Proposito = "ElastiCache Redis encryption at rest"
    Requisito = "12.5"
  })
}

resource "aws_kms_alias" "redis" {
  name          = "alias/${var.nombre_entorno}-redis"
  target_key_id = aws_kms_key.redis.key_id
}

# -------------------------------------------------------
# Subnet Group para ElastiCache
# -------------------------------------------------------
resource "aws_elasticache_subnet_group" "redis" {
  name        = "${var.nombre_entorno}-redis-subnet-group"
  description = "Subnet group para ElastiCache Redis"
  subnet_ids  = var.subnet_ids_privadas

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-redis-subnet-group"
  })
}

# -------------------------------------------------------
# Security Group para ElastiCache Redis
# -------------------------------------------------------
resource "aws_security_group" "redis" {
  name        = "${var.nombre_entorno}-redis-sg"
  description = "Security group para ElastiCache Redis — solo acceso desde EKS"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Redis desde EKS"
    from_port       = 6379
    to_port         = 6379
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
    Nombre = "${var.nombre_entorno}-redis-sg"
  })
}

# -------------------------------------------------------
# ElastiCache Redis Replication Group (cluster mode)
# at_rest_encryption_enabled = true  — Requisito 12.5
# transit_encryption_enabled = true  — Requisito 12.1/12.2
# -------------------------------------------------------
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = "${var.nombre_entorno}-redis"
  description          = "Redis cluster para plataforma bancaria — ${var.nombre_entorno}"

  node_type            = var.node_type
  num_cache_clusters   = var.num_cache_clusters
  parameter_group_name = aws_elasticache_parameter_group.redis7.name
  engine_version       = var.engine_version
  port                 = 6379

  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.redis.id]

  # Cifrado en reposo con KMS — satisface Requisito 12.5
  at_rest_encryption_enabled = true
  kms_key_id                 = aws_kms_key.redis.arn

  # Cifrado en tránsito (TLS) — satisface Requisitos 12.1, 12.2
  transit_encryption_enabled = true

  # Alta disponibilidad: failover automático
  automatic_failover_enabled = var.num_cache_clusters > 1 ? true : false
  multi_az_enabled           = var.num_cache_clusters > 1 ? true : false

  # Backups diarios
  snapshot_retention_limit = 7
  snapshot_window          = "03:00-04:00"
  maintenance_window       = "sun:04:00-sun:05:00"

  # La contraseña de autenticación se gestiona vía Secrets Manager (Requisito 12.3)
  auth_token                 = var.auth_token
  auth_token_update_strategy = "ROTATE"

  apply_immediately = false

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-redis"
    Requisito = "12.5"
  })
}

# -------------------------------------------------------
# Parameter Group para Redis 7.x
# -------------------------------------------------------
resource "aws_elasticache_parameter_group" "redis7" {
  name        = "${var.nombre_entorno}-redis7-params"
  family      = "redis7"
  description = "Parámetros Redis 7 para plataforma bancaria"

  parameter {
    name  = "maxmemory-policy"
    value = "allkeys-lru"
  }

  tags = var.tags_comunes
}
