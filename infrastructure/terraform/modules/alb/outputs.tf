output "alb_arn" {
  description = "ARN del Application Load Balancer"
  value       = aws_lb.principal.arn
}

output "alb_dns_name" {
  description = "Nombre DNS del ALB"
  value       = aws_lb.principal.dns_name
}

output "alb_zone_id" {
  description = "Zone ID del ALB (para registros Route 53 alias)"
  value       = aws_lb.principal.zone_id
}

output "security_group_id" {
  description = "ID del security group del ALB"
  value       = aws_security_group.alb.id
}

output "https_listener_arn" {
  description = "ARN del listener HTTPS del ALB"
  value       = aws_lb_listener.https.arn
}
