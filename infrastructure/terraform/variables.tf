# ============================================================
# Variables del módulo raíz Terraform
# Plataforma bancaria — infraestructura AWS
# ============================================================

variable "nombre_entorno" {
  description = "Nombre del entorno (ej: prod, staging)"
  type        = string
  default     = "prod"
}

variable "region_primaria" {
  description = "Región AWS primaria"
  type        = string
  default     = "us-east-1"
}

variable "region_dr" {
  description = "Región AWS de recuperación ante desastres"
  type        = string
  default     = "us-west-2"
}

# -------------------------------------------------------
# Variables de red — región primaria
# -------------------------------------------------------
variable "vpc_id" {
  description = "ID de la VPC principal (us-east-1)"
  type        = string
}

variable "subnet_ids_privadas" {
  description = "IDs de subnets privadas (una por AZ) para recursos internos"
  type        = list(string)
}

variable "subnet_ids_publicas" {
  description = "IDs de subnets públicas (una por AZ) para el ALB"
  type        = list(string)
}

# -------------------------------------------------------
# Variables de red — región DR
# -------------------------------------------------------
variable "vpc_id_dr" {
  description = "ID de la VPC en la región DR (us-west-2)"
  type        = string
}

variable "subnet_ids_publicas_dr" {
  description = "IDs de subnets públicas en la región DR"
  type        = list(string)
}

# -------------------------------------------------------
# Variables de dominio y certificados
# -------------------------------------------------------
variable "domain_name" {
  description = "Nombre de dominio principal (ej: banco.example.com)"
  type        = string
}

variable "certificate_arn" {
  description = "ARN del certificado ACM en us-east-1 para ALB y CloudFront"
  type        = string
}

variable "certificate_arn_dr" {
  description = "ARN del certificado ACM en us-west-2 para el ALB DR"
  type        = string
}

# -------------------------------------------------------
# Variables de EKS
# -------------------------------------------------------
variable "kubernetes_version" {
  description = "Versión de Kubernetes para el cluster EKS"
  type        = string
  default     = "1.29"
}

variable "node_instance_type" {
  description = "Tipo de instancia EC2 para los nodos EKS"
  type        = string
  default     = "m6i.2xlarge"
}

# -------------------------------------------------------
# Variables de bases de datos
# -------------------------------------------------------
variable "oracle_instance_class" {
  description = "Clase de instancia para Oracle RDS primario"
  type        = string
  default     = "db.r6g.2xlarge"
}

variable "redis_node_type" {
  description = "Tipo de nodo para ElastiCache Redis"
  type        = string
  default     = "cache.r7g.large"
}

variable "redis_auth_token" {
  description = "Token de autenticación Redis (gestionado vía Secrets Manager)"
  type        = string
  sensitive   = true
}

variable "documentdb_instance_class" {
  description = "Clase de instancia para DocumentDB"
  type        = string
  default     = "db.r6g.large"
}

# -------------------------------------------------------
# Variables de MSK
# -------------------------------------------------------
variable "broker_instance_type" {
  description = "Tipo de instancia para los brokers MSK"
  type        = string
  default     = "kafka.m5.2xlarge"
}

# -------------------------------------------------------
# Variables de S3
# -------------------------------------------------------
variable "s3_logs_bucket" {
  description = "Nombre del bucket S3 para access logs del ALB"
  type        = string
}

# -------------------------------------------------------
# Tags comunes para todos los recursos
# -------------------------------------------------------
variable "tags_comunes" {
  description = "Tags comunes aplicados a todos los recursos"
  type        = map(string)
  default = {
    Proyecto   = "plataforma-bancaria"
    Gestionado = "terraform"
  }
}
