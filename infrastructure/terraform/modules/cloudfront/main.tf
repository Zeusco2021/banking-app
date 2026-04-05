# ============================================================
# Módulo Terraform: Amazon CloudFront con ALB como origen
# HTTPS obligatorio, TLS 1.2 mínimo, WAF asociado
# Requisito 12.2: TLS en comunicaciones externas
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
# Distribución CloudFront con ALB como origen
# Sin caché para APIs (default TTL = 0), compresión habilitada
# -------------------------------------------------------
resource "aws_cloudfront_distribution" "principal" {
  enabled         = true
  is_ipv6_enabled = true
  comment         = "Distribución CloudFront para plataforma bancaria — ${var.nombre_entorno}"
  price_class     = "PriceClass_100"

  # Origen: ALB de la región primaria
  origin {
    domain_name = var.alb_dns_name
    origin_id   = "${var.nombre_entorno}-alb-origin"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  # Comportamiento por defecto — sin caché para APIs bancarias
  default_cache_behavior {
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "${var.nombre_entorno}-alb-origin"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    # Sin caché para garantizar datos en tiempo real
    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0

    forwarded_values {
      query_string = true
      headers      = ["*"]
      cookies {
        forward = "all"
      }
    }
  }

  # Restricciones geográficas — sin restricciones por defecto
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # Certificado ACM (debe estar en us-east-1 para CloudFront)
  # TLS 1.2 mínimo — Requisito 12.2
  viewer_certificate {
    acm_certificate_arn      = var.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  # Asociación WAF (scope CLOUDFRONT, debe ser us-east-1)
  # Requisito 12.4: WAF con reglas OWASP Top 10
  web_acl_id = var.waf_acl_arn

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-cloudfront"
    Requisito = "12.2,12.4"
  })
}
