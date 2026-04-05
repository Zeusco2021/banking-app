# ============================================================
# Módulo Terraform: Amazon MSK (Managed Streaming for Kafka)
# Requisitos 4.4, 4.5, 10.6: mensajería asíncrona y Outbox Pattern
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
# KMS Key para cifrado en reposo de MSK
# Requisito 12.5: cifrado en reposo
# -------------------------------------------------------
resource "aws_kms_key" "msk" {
  description             = "KMS key para cifrado en reposo de MSK Kafka — ${var.nombre_entorno}"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-msk-kms"
    Proposito = "MSK Kafka encryption at rest"
    Requisito = "12.5"
  })
}

resource "aws_kms_alias" "msk" {
  name          = "alias/${var.nombre_entorno}-msk"
  target_key_id = aws_kms_key.msk.key_id
}

# -------------------------------------------------------
# Security Group para los brokers MSK
# Permite puertos 9092 (plaintext) y 9094 (TLS) desde EKS
# -------------------------------------------------------
resource "aws_security_group" "msk" {
  name        = "${var.nombre_entorno}-msk-sg"
  description = "Security group para MSK Kafka — acceso desde EKS"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Kafka plaintext desde EKS"
    from_port       = 9092
    to_port         = 9092
    protocol        = "tcp"
    security_groups = var.security_group_ids_eks
  }

  ingress {
    description     = "Kafka TLS desde EKS"
    from_port       = 9094
    to_port         = 9094
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
    Nombre = "${var.nombre_entorno}-msk-sg"
  })
}

# -------------------------------------------------------
# Grupo de logs CloudWatch para los brokers MSK
# -------------------------------------------------------
resource "aws_cloudwatch_log_group" "msk_broker" {
  name              = "/aws/msk/${var.nombre_entorno}/broker"
  retention_in_days = 30

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-msk-broker-logs"
  })
}

# -------------------------------------------------------
# Cluster MSK con 3 brokers (uno por AZ)
# Kafka 3.5.x, cifrado TLS en tránsito y KMS en reposo
# Requisitos 4.4, 4.5, 10.6
# -------------------------------------------------------
resource "aws_msk_cluster" "principal" {
  cluster_name           = "${var.nombre_entorno}-msk"
  kafka_version          = "3.5.1"
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type   = var.broker_instance_type
    client_subnets  = var.subnet_ids_privadas
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = 1000
      }
    }
  }

  # Cifrado en tránsito — TLS obligatorio entre clientes y brokers
  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
    # Cifrado en reposo con KMS — Requisito 12.5
    encryption_at_rest_kms_key_arn = aws_kms_key.msk.arn
  }

  # Logs de broker en CloudWatch
  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk_broker.name
      }
    }
  }

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-msk"
    Requisito = "4.4,4.5,10.6"
  })
}
