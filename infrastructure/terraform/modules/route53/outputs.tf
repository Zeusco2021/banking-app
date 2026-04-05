output "zone_id" {
  description = "ID de la hosted zone de Route 53"
  value       = data.aws_route53_zone.principal.zone_id
}

output "primary_health_check_id" {
  description = "ID del health check de la región primaria"
  value       = aws_route53_health_check.primario.id
}

output "dr_health_check_id" {
  description = "ID del health check de la región DR"
  value       = aws_route53_health_check.dr.id
}
