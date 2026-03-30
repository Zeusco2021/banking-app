# CloudWatch Alarms for Banking Platform
# Covers: CPU > 90%, HTTP 5xx error rate > 1%, p99 latency > 2s
# Requires: AWS provider configured in parent module

locals {
  microservices = [
    "auth-service",
    "account-service",
    "transaction-service",
    "notification-service",
    "audit-service",
    "api-gateway-config",
    "legacy-adapter",
  ]
}

# SNS topic for all banking platform alerts
resource "aws_sns_topic" "banking_platform_alerts" {
  name = "banking-platform-alerts"

  tags = {
    Project     = "banking-platform"
    Environment = "production"
  }
}

# CPU utilization alarms per microservice (EKS Container Insights)
resource "aws_cloudwatch_metric_alarm" "cpu_high" {
  for_each = toset(local.microservices)

  alarm_name          = "banking-${each.key}-cpu-high"
  alarm_description   = "CPU utilization > 90% for ${each.key}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "pod_cpu_utilization"
  namespace           = "ContainerInsights"
  period              = 60
  statistic           = "Average"
  threshold           = 90
  treat_missing_data  = "notBreaching"

  dimensions = {
    PodName     = each.key
    ClusterName = "banking-platform"
  }

  alarm_actions = [aws_sns_topic.banking_platform_alerts.arn]
  ok_actions    = [aws_sns_topic.banking_platform_alerts.arn]

  tags = {
    Project     = "banking-platform"
    Service     = each.key
    AlertType   = "cpu"
  }
}

# HTTP 5xx error rate > 1% on API Gateway
resource "aws_cloudwatch_metric_alarm" "api_gateway_5xx_error_rate" {
  alarm_name          = "banking-api-gateway-5xx-error-rate-high"
  alarm_description   = "API Gateway 5xx error rate > 1%"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  threshold           = 1
  treat_missing_data  = "notBreaching"

  metric_query {
    id          = "error_rate"
    expression  = "(m_5xx / m_total) * 100"
    label       = "5xx Error Rate %"
    return_data = true
  }

  metric_query {
    id = "m_5xx"
    metric {
      metric_name = "5XXError"
      namespace   = "AWS/ApiGateway"
      period      = 60
      stat        = "Sum"
      dimensions = {
        ApiName = "banking-platform-api"
      }
    }
  }

  metric_query {
    id = "m_total"
    metric {
      metric_name = "Count"
      namespace   = "AWS/ApiGateway"
      period      = 60
      stat        = "Sum"
      dimensions = {
        ApiName = "banking-platform-api"
      }
    }
  }

  alarm_actions = [aws_sns_topic.banking_platform_alerts.arn]
  ok_actions    = [aws_sns_topic.banking_platform_alerts.arn]

  tags = {
    Project   = "banking-platform"
    AlertType = "error-rate"
  }
}

# p99 latency > 2000ms on API Gateway (IntegrationLatency)
resource "aws_cloudwatch_metric_alarm" "api_gateway_p99_latency" {
  alarm_name          = "banking-api-gateway-p99-latency-high"
  alarm_description   = "API Gateway p99 integration latency > 2000ms"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "IntegrationLatency"
  namespace           = "AWS/ApiGateway"
  period              = 60
  extended_statistic  = "p99"
  threshold           = 2000
  treat_missing_data  = "notBreaching"

  dimensions = {
    ApiName = "banking-platform-api"
  }

  alarm_actions = [aws_sns_topic.banking_platform_alerts.arn]
  ok_actions    = [aws_sns_topic.banking_platform_alerts.arn]

  tags = {
    Project   = "banking-platform"
    AlertType = "latency"
  }
}
