output "distribution_id" {
  description = "ID de la distribución CloudFront"
  value       = aws_cloudfront_distribution.principal.id
}

output "distribution_domain_name" {
  description = "Nombre de dominio de la distribución CloudFront"
  value       = aws_cloudfront_distribution.principal.domain_name
}

output "distribution_hosted_zone_id" {
  description = "Zone ID de CloudFront (para registros Route 53 alias)"
  value       = aws_cloudfront_distribution.principal.hosted_zone_id
}
