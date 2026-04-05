# ============================================================
# Módulo Terraform: S3 con SSE-KMS (Server-Side Encryption)
# Requisito 12.5: cifrado en reposo en S3 (SSE-KMS)
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
# KMS Key para cifrado SSE-KMS de S3
# -------------------------------------------------------
resource "aws_kms_key" "s3" {
  description             = "KMS key para SSE-KMS de S3 — ${var.nombre_entorno}"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-s3-kms"
    Proposito = "S3 SSE-KMS"
    Requisito = "12.5"
  })
}

resource "aws_kms_alias" "s3" {
  name          = "alias/${var.nombre_entorno}-s3"
  target_key_id = aws_kms_key.s3.key_id
}

# -------------------------------------------------------
# Buckets S3 con SSE-KMS
# Se crean múltiples buckets según var.buckets (reportes, backups, artefactos CI/CD)
# -------------------------------------------------------
resource "aws_s3_bucket" "buckets" {
  for_each = toset(var.nombres_buckets)

  bucket        = "${var.nombre_entorno}-${each.key}"
  force_destroy = var.force_destroy

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-${each.key}"
    Requisito = "12.5"
  })
}

# Bloquear todo acceso público
resource "aws_s3_bucket_public_access_block" "buckets" {
  for_each = aws_s3_bucket.buckets

  bucket = each.value.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Cifrado SSE-KMS — satisface Requisito 12.5
resource "aws_s3_bucket_server_side_encryption_configuration" "buckets" {
  for_each = aws_s3_bucket.buckets

  bucket = each.value.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.s3.arn
    }
    # Forzar SSE-KMS en todas las cargas (rechaza cargas sin cifrado o con SSE-S3)
    bucket_key_enabled = true
  }
}

# Versionado habilitado para auditoría y recuperación
resource "aws_s3_bucket_versioning" "buckets" {
  for_each = aws_s3_bucket.buckets

  bucket = each.value.id

  versioning_configuration {
    status = "Enabled"
  }
}

# Política de ciclo de vida: mover a Glacier tras 90 días, expirar tras 7 años
# (Requisito 6.5: retención de auditoría 7 años)
resource "aws_s3_bucket_lifecycle_configuration" "buckets" {
  for_each = aws_s3_bucket.buckets

  bucket = each.value.id

  rule {
    id     = "archive-and-expire"
    status = "Enabled"

    transition {
      days          = 90
      storage_class = "GLACIER"
    }

    expiration {
      days = 2555 # 7 años
    }

    noncurrent_version_transition {
      noncurrent_days = 30
      storage_class   = "GLACIER"
    }

    noncurrent_version_expiration {
      noncurrent_days = 2555
    }
  }
}

# Política de bucket: denegar cargas sin cifrado SSE-KMS
resource "aws_s3_bucket_policy" "enforce_encryption" {
  for_each = aws_s3_bucket.buckets

  bucket = each.value.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyNonKMSUploads"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:PutObject"
        Resource  = "${each.value.arn}/*"
        Condition = {
          StringNotEquals = {
            "s3:x-amz-server-side-encryption" = "aws:kms"
          }
        }
      },
      {
        Sid       = "DenyHTTP"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          each.value.arn,
          "${each.value.arn}/*"
        ]
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      }
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.buckets]
}
