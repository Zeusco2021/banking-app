# ============================================================
# Módulo Terraform: Route 53 con Health Checks y Failover
# Requisito 10.5: failover automático hacia región DR (us-west-2)
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
# Hosted Zone — se usa data source si ya existe
# -------------------------------------------------------
data "aws_route53_zone" "principal" {
  name         = var.domain_name
  private_zone = false
}

# -------------------------------------------------------
# Health Check para la región primaria (us-east-1)
# Requisito 10.5: health check para failover automático
# -------------------------------------------------------
resource "aws_route53_health_check" "primario" {
  fqdn              = var.primary_alb_dns
  port              = 443
  type              = "HTTPS"
  resource_path     = "/actuator/health"
  failure_threshold = 3
  request_interval  = 30

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-hc-primario"
    Region    = "us-east-1"
    Requisito = "10.5"
  })
}

# -------------------------------------------------------
# Health Check para la región DR (us-west-2)
# Requisito 10.5: health check para failover automático
# -------------------------------------------------------
resource "aws_route53_health_check" "dr" {
  fqdn              = var.dr_alb_dns
  port              = 443
  type              = "HTTPS"
  resource_path     = "/actuator/health"
  failure_threshold = 3
  request_interval  = 30

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-hc-dr"
    Region    = "us-west-2"
    Requisito = "10.5"
  })
}

# -------------------------------------------------------
# Registro DNS PRIMARY — apunta a CloudFront (región primaria)
# Política de failover: PRIMARY
# Requisito 10.5: failover automático
# -------------------------------------------------------
resource "aws_route53_record" "primario" {
  zone_id = data.aws_route53_zone.principal.zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = var.primary_alb_dns
    zone_id                = var.primary_alb_zone_id
    evaluate_target_health = true
  }

  failover_routing_policy {
    type = "PRIMARY"
  }

  set_identifier  = "${var.nombre_entorno}-primario"
  health_check_id = aws_route53_health_check.primario.id
}

# -------------------------------------------------------
# Registro DNS SECONDARY — apunta al ALB de la región DR
# Política de failover: SECONDARY (activa si PRIMARY falla)
# Requisito 10.5: failover automático hacia us-west-2
# -------------------------------------------------------
resource "aws_route53_record" "dr" {
  zone_id = data.aws_route53_zone.principal.zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = var.dr_alb_dns
    zone_id                = var.dr_alb_zone_id
    evaluate_target_health = true
  }

  failover_routing_policy {
    type = "SECONDARY"
  }

  set_identifier  = "${var.nombre_entorno}-dr"
  health_check_id = aws_route53_health_check.dr.id
}
