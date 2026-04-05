output "cluster_endpoint" {
  description = "Endpoint de escritura del cluster DocumentDB"
  value       = aws_docdb_cluster.principal.endpoint
}

output "reader_endpoint" {
  description = "Endpoint de lectura del cluster DocumentDB"
  value       = aws_docdb_cluster.principal.reader_endpoint
}

output "cluster_arn" {
  description = "ARN del cluster DocumentDB"
  value       = aws_docdb_cluster.principal.arn
}

output "documentdb_kms_key_arn" {
  description = "ARN de la KMS key usada para cifrado en reposo de DocumentDB"
  value       = aws_kms_key.documentdb.arn
}

output "master_user_secret_arn" {
  description = "ARN del secreto en Secrets Manager con las credenciales del master user"
  value       = aws_docdb_cluster.principal.master_user_secret[0].secret_arn
}
