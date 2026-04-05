variable "nombre_entorno" {
  description = "Nombre del entorno (ej: prod, staging)"
  type        = string
}

variable "vpc_id" {
  description = "ID de la VPC donde se despliega ElastiCache Redis"
  type        = string
}

variable "subnet_ids_privadas" {
  description = "Lista de IDs de subnets privadas para el subnet group de ElastiCache"
  type        = list(string)
}

variable "security_group_ids_eks" {
  description = "IDs de security groups de los nodos EKS que acceden a Redis"
  type        = list(string)
}

variable "node_type" {
  description = "Tipo de nodo ElastiCache (ej: cache.r7g.large)"
  type        = string
  default     = "cache.r7g.large"
}

variable "num_cache_clusters" {
  description = "Número de nodos en el replication group (mínimo 2 para Multi-AZ)"
  type        = number
  default     = 3
}

variable "engine_version" {
  description = "Versión del motor Redis"
  type        = string
  default     = "7.1"
}

variable "auth_token" {
  description = "Token de autenticación Redis (cargado desde Secrets Manager en el módulo raíz)"
  type        = string
  sensitive   = true
}

variable "tags_comunes" {
  description = "Tags comunes aplicados a todos los recursos"
  type        = map(string)
  default     = {}
}
