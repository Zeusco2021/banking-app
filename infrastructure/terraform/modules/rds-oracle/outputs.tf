output "oracle_primary_endpoint" {
  description = "Endpoint de conexión del nodo primario Oracle RDS"
  value       = aws_db_instance.oracle_primary.endpoint
}

output "oracle_replica_endpoint" {
  description = "Endpoint de conexión de la réplica de lectura Oracle RDS"
  value       = aws_db_instance.oracle_replica.endpoint
}

output "oracle_kms_key_arn" {
  description = "ARN de la KMS key usada para cifrado TDE"
  value       = aws_kms_key.rds_oracle.arn
}

output "oracle_kms_key_id" {
  description = "ID de la KMS key usada para cifrado TDE"
  value       = aws_kms_key.rds_oracle.key_id
}

output "oracle_primary_identifier" {
  description = "Identificador del nodo primario Oracle RDS"
  value       = aws_db_instance.oracle_primary.identifier
}

output "master_user_secret_arn" {
  description = "ARN del secreto en Secrets Manager que contiene las credenciales del master user"
  value       = aws_db_instance.oracle_primary.master_user_secret[0].secret_arn
}

output "cross_region_replica_endpoint" {
  description = "Endpoint de la réplica cross-region Oracle RDS en us-west-2 (vacío si create_cross_region_replica = false)"
  value       = var.create_cross_region_replica ? aws_db_instance.oracle_cross_region_replica[0].endpoint : ""
}
