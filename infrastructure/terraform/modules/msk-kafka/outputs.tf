output "bootstrap_brokers_tls" {
  description = "Cadena de conexión TLS a los brokers MSK"
  value       = aws_msk_cluster.principal.bootstrap_brokers_tls
}

output "cluster_arn" {
  description = "ARN del cluster MSK"
  value       = aws_msk_cluster.principal.arn
}

output "zookeeper_connect_string" {
  description = "Cadena de conexión a ZooKeeper del cluster MSK"
  value       = aws_msk_cluster.principal.zookeeper_connect_string
}

output "msk_kms_key_arn" {
  description = "ARN de la KMS key usada para cifrado en reposo de MSK"
  value       = aws_kms_key.msk.arn
}
