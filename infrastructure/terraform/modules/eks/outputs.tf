output "cluster_name" {
  description = "Nombre del cluster EKS"
  value       = aws_eks_cluster.principal.name
}

output "cluster_endpoint" {
  description = "Endpoint del plano de control de EKS"
  value       = aws_eks_cluster.principal.endpoint
}

output "cluster_ca_certificate" {
  description = "Certificado CA del cluster EKS (base64)"
  value       = aws_eks_cluster.principal.certificate_authority[0].data
}

output "cluster_security_group_id" {
  description = "ID del security group de los nodos EKS"
  value       = aws_security_group.eks_nodes.id
}

output "node_group_role_arn" {
  description = "ARN del IAM role de los nodos EKS"
  value       = aws_iam_role.eks_node_group.arn
}

output "oidc_provider_arn" {
  description = "ARN del OIDC provider para IRSA"
  value       = aws_iam_openid_connect_provider.eks.arn
}

output "cluster_autoscaler_role_arn" {
  description = "ARN del IAM role para Cluster Autoscaler (IRSA)"
  value       = aws_iam_role.cluster_autoscaler.arn
}
