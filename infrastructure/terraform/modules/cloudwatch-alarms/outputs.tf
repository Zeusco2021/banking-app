# ============================================================
# Outputs del módulo cloudwatch-alarms
# ============================================================

output "sns_topic_arn" {
  description = "ARN del topic SNS de alertas de la plataforma"
  value       = aws_sns_topic.alertas_plataforma.arn
}

output "alarmas_cpu_arns" {
  description = "ARNs de las alarmas de CPU por servicio"
  value       = { for k, v in aws_cloudwatch_metric_alarm.cpu_alto : k => v.arn }
}

output "alarmas_error_rate_arns" {
  description = "ARNs de las alarmas de error rate por servicio"
  value       = { for k, v in aws_cloudwatch_metric_alarm.error_rate_alto : k => v.arn }
}

output "alarmas_latencia_arns" {
  description = "ARNs de las alarmas de latencia p99 por servicio"
  value       = { for k, v in aws_cloudwatch_metric_alarm.latencia_p99_alta : k => v.arn }
}

output "dashboard_cloudwatch_url" {
  description = "URL del dashboard CloudWatch de resumen"
  value       = "https://console.aws.amazon.com/cloudwatch/home#dashboards:name=${aws_cloudwatch_dashboard.resumen_plataforma.dashboard_name}"
}
