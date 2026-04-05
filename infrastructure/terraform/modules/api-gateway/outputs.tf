output "api_id" {
  description = "ID del REST API de API Gateway"
  value       = aws_api_gateway_rest_api.principal.id
}

output "stage_invoke_url" {
  description = "URL de invocación del stage del API Gateway"
  value       = aws_api_gateway_stage.principal.invoke_url
}

output "api_key_value" {
  description = "Valor de la API key para desarrolladores externos"
  value       = aws_api_gateway_api_key.desarrolladores.value
  sensitive   = true
}
