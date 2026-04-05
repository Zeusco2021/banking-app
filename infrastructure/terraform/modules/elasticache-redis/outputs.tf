output "redis_primary_endpoint" {
  description = "Endpoint del nodo primario Redis"
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "redis_reader_endpoint" {
  description = "Endpoint de lectura Redis (round-robin entre réplicas)"
  value       = aws_elasticache_replication_group.redis.reader_endpoint_address
}

output "redis_port" {
  description = "Puerto Redis"
  value       = aws_elasticache_replication_group.redis.port
}

output "redis_kms_key_arn" {
  description = "ARN de la KMS key usada para cifrado en reposo de Redis"
  value       = aws_kms_key.redis.arn
}

output "redis_kms_key_id" {
  description = "ID de la KMS key usada para cifrado en reposo de Redis"
  value       = aws_kms_key.redis.key_id
}
