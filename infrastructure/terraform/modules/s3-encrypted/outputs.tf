output "bucket_arns" {
  description = "Mapa de nombre de bucket → ARN"
  value       = { for k, v in aws_s3_bucket.buckets : k => v.arn }
}

output "bucket_names" {
  description = "Mapa de sufijo → nombre completo del bucket"
  value       = { for k, v in aws_s3_bucket.buckets : k => v.id }
}

output "s3_kms_key_arn" {
  description = "ARN de la KMS key usada para SSE-KMS en S3"
  value       = aws_kms_key.s3.arn
}

output "s3_kms_key_id" {
  description = "ID de la KMS key usada para SSE-KMS en S3"
  value       = aws_kms_key.s3.key_id
}
