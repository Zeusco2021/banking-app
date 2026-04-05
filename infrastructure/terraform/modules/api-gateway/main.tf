# ============================================================
# Módulo Terraform: AWS API Gateway REST API
# Requisitos 1.1, 1.4, 8.5: punto de entrada único, throttling, rate limiting
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
# Grupo de logs CloudWatch para access logging del API Gateway
# Requisito 11.3: centralización de logs
# -------------------------------------------------------
resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/aws/apigateway/${var.nombre_entorno}"
  retention_in_days = 30

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-api-gateway-logs"
  })
}

# -------------------------------------------------------
# REST API con endpoint regional
# Requisito 1.1: punto de entrada único para todas las APIs bancarias
# -------------------------------------------------------
resource "aws_api_gateway_rest_api" "principal" {
  name        = "${var.nombre_entorno}-api"
  description = "API Gateway principal para la plataforma bancaria — ${var.nombre_entorno}"

  endpoint_configuration {
    types = ["REGIONAL"]
  }

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-api"
    Requisito = "1.1,1.4,8.5"
  })
}

# -------------------------------------------------------
# Deployment del API Gateway
# -------------------------------------------------------
resource "aws_api_gateway_deployment" "principal" {
  rest_api_id = aws_api_gateway_rest_api.principal.id

  lifecycle {
    create_before_destroy = true
  }
}

# -------------------------------------------------------
# Stage con access logging y throttling a nivel de stage
# Requisito 11.3: logs centralizados
# Requisito 8.5: throttling adicional por API key en API Gateway
# -------------------------------------------------------
resource "aws_api_gateway_stage" "principal" {
  deployment_id = aws_api_gateway_deployment.principal.id
  rest_api_id   = aws_api_gateway_rest_api.principal.id
  stage_name    = var.nombre_entorno

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway.arn
    format = jsonencode({
      requestId      = "$context.requestId"
      ip             = "$context.identity.sourceIp"
      caller         = "$context.identity.caller"
      user           = "$context.identity.user"
      requestTime    = "$context.requestTime"
      httpMethod     = "$context.httpMethod"
      resourcePath   = "$context.resourcePath"
      status         = "$context.status"
      protocol       = "$context.protocol"
      responseLength = "$context.responseLength"
      apiKeyId       = "$context.identity.apiKeyId"
    })
  }

  # Stage-level default throttling — capa sobre Redis Rate Limiter
  # Requisito 8.5: throttling adicional por API key en AWS API Gateway
  default_route_settings {
    throttling_rate_limit  = var.stage_throttle_rate_limit
    throttling_burst_limit = var.stage_throttle_burst_limit
  }

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-api-stage"
    Requisito = "8.5,11.3"
  })
}

# -------------------------------------------------------
# Usage Plan con throttling
# Requisito 8.5: throttling adicional por API key en API Gateway
# rate_limit = 10000 req/s, burst_limit = 5000
# -------------------------------------------------------
resource "aws_api_gateway_usage_plan" "principal" {
  name        = "${var.nombre_entorno}-usage-plan"
  description = "Plan de uso con throttling para la plataforma bancaria"

  api_stages {
    api_id = aws_api_gateway_rest_api.principal.id
    stage  = aws_api_gateway_stage.principal.stage_name
  }

  throttle_settings {
    rate_limit  = 10000
    burst_limit = 5000
  }

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-usage-plan"
    Requisito = "8.5"
  })
}

# -------------------------------------------------------
# API Key para desarrolladores externos
# Requisito 1.4: control de acceso por API key
# -------------------------------------------------------
resource "aws_api_gateway_api_key" "desarrolladores" {
  name        = "${var.nombre_entorno}-api-key-desarrolladores"
  description = "API key para desarrolladores externos — ${var.nombre_entorno}"
  enabled     = true

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-api-key-desarrolladores"
    Requisito = "1.4"
  })
}

# -------------------------------------------------------
# Asociar API key al Usage Plan
# -------------------------------------------------------
resource "aws_api_gateway_usage_plan_key" "desarrolladores" {
  key_id        = aws_api_gateway_api_key.desarrolladores.id
  key_type      = "API_KEY"
  usage_plan_id = aws_api_gateway_usage_plan.principal.id
}

# -------------------------------------------------------
# Asociación WAF — protección OWASP Top 10
# Requisito 12.4: WAF antes de que las solicitudes lleguen al API Gateway
# -------------------------------------------------------
resource "aws_wafv2_web_acl_association" "api_gateway" {
  resource_arn = aws_api_gateway_stage.principal.arn
  web_acl_arn  = var.waf_acl_arn
}
