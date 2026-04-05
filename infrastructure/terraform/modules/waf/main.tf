# ============================================================
# Módulo Terraform: AWS WAF v2 con reglas OWASP Top 10
# Requisito 12.4: WAF con reglas OWASP antes del API Gateway
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
# Web ACL con reglas administradas OWASP Top 10
# y regla de rate limiting por IP
# Requisito 12.4: protección contra SQL injection, XSS, etc.
# -------------------------------------------------------
resource "aws_wafv2_web_acl" "principal" {
  name        = "${var.nombre_entorno}-waf-acl"
  description = "Web ACL con reglas OWASP Top 10 para plataforma bancaria — ${var.nombre_entorno}"
  scope       = var.scope

  default_action {
    allow {}
  }

  # Regla 1: Conjunto de reglas comunes de AWS (OWASP Top 10)
  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 10

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.nombre_entorno}-waf-common-rules"
      sampled_requests_enabled   = true
    }
  }

  # Regla 2: Protección contra SQL Injection
  rule {
    name     = "AWSManagedRulesSQLiRuleSet"
    priority = 20

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesSQLiRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.nombre_entorno}-waf-sqli-rules"
      sampled_requests_enabled   = true
    }
  }

  # Regla 3: Protección contra entradas maliciosas conocidas
  rule {
    name     = "AWSManagedRulesKnownBadInputsRuleSet"
    priority = 30

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.nombre_entorno}-waf-bad-inputs-rules"
      sampled_requests_enabled   = true
    }
  }

  # Regla 4: Rate limiting — 2000 solicitudes por 5 minutos por IP
  rule {
    name     = "RateLimitingPorIP"
    priority = 40

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.nombre_entorno}-waf-rate-limit"
      sampled_requests_enabled   = true
    }
  }

  # Configuración de métricas CloudWatch para el Web ACL completo
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.nombre_entorno}-waf-acl"
    sampled_requests_enabled   = true
  }

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-waf-acl"
    Requisito = "12.4"
  })
}
