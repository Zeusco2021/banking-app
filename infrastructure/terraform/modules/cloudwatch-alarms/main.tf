# ============================================================
# Módulo Terraform: Alarmas CloudWatch para microservicios bancarios
# Requisitos: 11.4
# Cubre: CPU > 90%, error rate > 1%, latencia p99 > 2s
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
# Topic SNS para notificaciones de alarmas
# -------------------------------------------------------
resource "aws_sns_topic" "alertas_plataforma" {
  name              = "${var.nombre_entorno}-alertas-plataforma"
  kms_master_key_id = "alias/aws/sns"

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-alertas-plataforma"
  })
}

resource "aws_sns_topic_subscription" "email_ops" {
  count     = length(var.emails_operaciones)
  topic_arn = aws_sns_topic.alertas_plataforma.arn
  protocol  = "email"
  endpoint  = var.emails_operaciones[count.index]
}

# -------------------------------------------------------
# Alarmas por servicio (iteración sobre la lista de servicios)
# -------------------------------------------------------
locals {
  servicios = [
    "api-gateway",
    "auth-service",
    "account-service",
    "transaction-service",
    "notification-service",
    "audit-service",
  ]
}

# --- CPU > 90% ---
resource "aws_cloudwatch_metric_alarm" "cpu_alto" {
  for_each = toset(local.servicios)

  alarm_name          = "${var.nombre_entorno}-${each.key}-cpu-alto"
  alarm_description   = "CPU del servicio ${each.key} supera el 90% — Requisito 11.4"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 90
  treat_missing_data  = "notBreaching"

  dimensions = {
    ServiceName = each.key
    ClusterName = var.nombre_cluster_ecs
  }

  alarm_actions = [aws_sns_topic.alertas_plataforma.arn]
  ok_actions    = [aws_sns_topic.alertas_plataforma.arn]

  tags = merge(var.tags_comunes, {
    Servicio = each.key
    Tipo     = "cpu"
  })
}

# --- Error rate HTTP > 1% ---
# Métrica personalizada publicada por cada servicio vía Micrometer/CloudWatch Embedded Metrics
resource "aws_cloudwatch_metric_alarm" "error_rate_alto" {
  for_each = toset(local.servicios)

  alarm_name          = "${var.nombre_entorno}-${each.key}-error-rate-alto"
  alarm_description   = "Error rate HTTP del servicio ${each.key} supera el 1% — Requisito 11.4"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  threshold           = 1
  treat_missing_data  = "notBreaching"

  # Métrica calculada: (errores_5xx / total_requests) * 100
  metric_query {
    id          = "error_rate"
    expression  = "(errores / total) * 100"
    label       = "Error Rate %"
    return_data = true
  }

  metric_query {
    id = "errores"
    metric {
      metric_name = "http_server_requests_seconds_count"
      namespace   = "BancoPlatforma/${each.key}"
      period      = 60
      stat        = "Sum"
      dimensions = {
        status = "5xx"
      }
    }
  }

  metric_query {
    id = "total"
    metric {
      metric_name = "http_server_requests_seconds_count"
      namespace   = "BancoPlatforma/${each.key}"
      period      = 60
      stat        = "Sum"
    }
  }

  alarm_actions = [aws_sns_topic.alertas_plataforma.arn]
  ok_actions    = [aws_sns_topic.alertas_plataforma.arn]

  tags = merge(var.tags_comunes, {
    Servicio = each.key
    Tipo     = "error-rate"
  })
}

# --- Latencia p99 > 2 segundos ---
resource "aws_cloudwatch_metric_alarm" "latencia_p99_alta" {
  for_each = toset(local.servicios)

  alarm_name          = "${var.nombre_entorno}-${each.key}-latencia-p99-alta"
  alarm_description   = "Latencia p99 del servicio ${each.key} supera 2s — Requisito 11.4"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  # Métrica publicada en segundos por Micrometer (http_server_requests_seconds)
  metric_name         = "http_server_requests_seconds"
  namespace           = "BancoPlatforma/${each.key}"
  period              = 60
  extended_statistic  = "p99"
  threshold           = 2
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alertas_plataforma.arn]
  ok_actions    = [aws_sns_topic.alertas_plataforma.arn]

  tags = merge(var.tags_comunes, {
    Servicio = each.key
    Tipo     = "latencia-p99"
  })
}

# -------------------------------------------------------
# Dashboard CloudWatch de resumen (complementa Grafana)
# -------------------------------------------------------
resource "aws_cloudwatch_dashboard" "resumen_plataforma" {
  dashboard_name = "${var.nombre_entorno}-plataforma-bancaria"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "alarm"
        x      = 0
        y      = 0
        width  = 24
        height = 6
        properties = {
          title  = "Estado de Alarmas — Plataforma Bancaria"
          alarms = concat(
            [for k, v in aws_cloudwatch_metric_alarm.cpu_alto : v.arn],
            [for k, v in aws_cloudwatch_metric_alarm.error_rate_alto : v.arn],
            [for k, v in aws_cloudwatch_metric_alarm.latencia_p99_alta : v.arn],
          )
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          title  = "CPU Utilización — Todos los Servicios"
          view   = "timeSeries"
          period = 60
          metrics = [
            for svc in local.servicios : ["AWS/ECS", "CPUUtilization", "ServiceName", svc, "ClusterName", var.nombre_cluster_ecs]
          ]
          annotations = {
            horizontal = [{ value = 90, label = "Umbral 90%", color = "#ff0000" }]
          }
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 6
        width  = 12
        height = 6
        properties = {
          title  = "Latencia p99 — Todos los Servicios"
          view   = "timeSeries"
          period = 60
          metrics = [
            for svc in local.servicios : ["BancoPlatforma/${svc}", "http_server_requests_seconds", { stat = "p99", label = svc }]
          ]
          annotations = {
            horizontal = [{ value = 2, label = "Umbral 2s", color = "#ff0000" }]
          }
        }
      }
    ]
  })
}
