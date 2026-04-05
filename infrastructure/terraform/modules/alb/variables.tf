variable "nombre_entorno" {
  description = "Nombre del entorno (ej: prod, staging)"
  type        = string
}

variable "vpc_id" {
  description = "ID de la VPC donde se despliega el ALB"
  type        = string
}

variable "subnet_ids_publicas" {
  description = "Lista de IDs de subnets públicas (una por AZ) para el ALB"
  type        = list(string)
}

variable "certificate_arn" {
  description = "ARN del certificado ACM para el listener HTTPS"
  type        = string
}

variable "s3_logs_bucket" {
  description = "Nombre del bucket S3 para access logs del ALB"
  type        = string
}

variable "tags_comunes" {
  description = "Tags comunes aplicados a todos los recursos"
  type        = map(string)
  default     = {}
}
