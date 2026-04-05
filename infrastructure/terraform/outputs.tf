# ============================================================
# Outputs del módulo raíz Terraform
# Plataforma bancaria — infraestructura AWS
# ============================================================

# -------------------------------------------------------
# Outputs de EKS
# -------------------------------------------------------
output "eks_cluster_name" {
  description = "Nombre del cluster EKS"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "Endpoint del plano de control de EKS"
  value       = module.eks.cluster_endpoint
}

output "eks_oidc_provider_arn" {
  description = "ARN del OIDC provider para IRSA"
  value       = module.eks.oidc_provider_arn
}

# -------------------------------------------------------
# Outputs de Oracle RDS
# -------------------------------------------------------
output "oracle_primary_endpoint" {
  description = "Endpoint del nodo primario Oracle RDS"
  value       = module.rds_oracle.oracle_primary_endpoint
}

output "oracle_replica_endpoint" {
  description = "Endpoint de la réplica de lectura Oracle RDS"
  value       = module.rds_oracle.oracle_replica_endpoint
}

output "oracle_cross_region_replica_endpoint" {
  description = "Endpoint de la réplica cross-region Oracle RDS (us-west-2)"
  value       = module.rds_oracle.cross_region_replica_endpoint
}

# -------------------------------------------------------
# Outputs de ElastiCache Redis
# -------------------------------------------------------
output "redis_primary_endpoint" {
  description = "Endpoint primario de ElastiCache Redis"
  value       = module.elasticache_redis.redis_primary_endpoint
}

# -------------------------------------------------------
# Outputs de MSK Kafka
# -------------------------------------------------------
output "msk_bootstrap_brokers_tls" {
  description = "Cadena de conexión TLS a los brokers MSK"
  value       = module.msk_kafka.bootstrap_brokers_tls
}

# -------------------------------------------------------
# Outputs de DocumentDB
# -------------------------------------------------------
output "documentdb_cluster_endpoint" {
  description = "Endpoint de escritura del cluster DocumentDB"
  value       = module.documentdb.cluster_endpoint
}

output "documentdb_reader_endpoint" {
  description = "Endpoint de lectura del cluster DocumentDB"
  value       = module.documentdb.reader_endpoint
}

# -------------------------------------------------------
# Outputs de ALB y CloudFront
# -------------------------------------------------------
output "alb_dns_name" {
  description = "Nombre DNS del ALB"
  value       = module.alb.alb_dns_name
}

output "cloudfront_domain_name" {
  description = "Nombre de dominio de la distribución CloudFront"
  value       = module.cloudfront.distribution_domain_name
}

# -------------------------------------------------------
# Outputs de API Gateway
# -------------------------------------------------------
output "api_gateway_invoke_url" {
  description = "URL de invocación del API Gateway"
  value       = module.api_gateway.stage_invoke_url
}

# -------------------------------------------------------
# Outputs de WAF
# -------------------------------------------------------
output "waf_regional_arn" {
  description = "ARN del Web ACL WAF regional"
  value       = module.waf_regional.waf_acl_arn
}

output "waf_cloudfront_arn" {
  description = "ARN del Web ACL WAF para CloudFront"
  value       = module.waf_cloudfront.waf_acl_arn
}
