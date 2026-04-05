variable "nombre_entorno" {
  description = "Nombre del entorno (ej: prod, staging)"
  type        = string
}

variable "vpc_id" {
  description = "ID de la VPC donde se despliega Oracle RDS"
  type        = string
}

variable "subnet_ids_privadas" {
  description = "Lista de IDs de subnets privadas para el DB subnet group"
  type        = list(string)
}

variable "security_group_ids_eks" {
  description = "IDs de security groups de los nodos EKS que acceden a Oracle"
  type        = list(string)
}

variable "oracle_engine_version" {
  description = "Versión del motor Oracle (ej: 19.0.0.0.ru-2024-01.rur-2024-01.r1)"
  type        = string
  default     = "19.0.0.0.ru-2024-01.rur-2024-01.r1"
}

variable "instance_class" {
  description = "Clase de instancia RDS para el nodo primario"
  type        = string
  default     = "db.r6g.2xlarge"
}

variable "instance_class_replica" {
  description = "Clase de instancia RDS para la réplica de lectura"
  type        = string
  default     = "db.r6g.xlarge"
}

variable "allocated_storage_gb" {
  description = "Almacenamiento inicial en GB"
  type        = number
  default     = 500
}

variable "max_allocated_storage_gb" {
  description = "Almacenamiento máximo en GB (autoscaling de storage)"
  type        = number
  default     = 2000
}

variable "db_name" {
  description = "Nombre de la base de datos Oracle"
  type        = string
  default     = "BANKDB"
}

variable "db_username" {
  description = "Usuario administrador de Oracle DB"
  type        = string
  default     = "bankadmin"
}

variable "tags_comunes" {
  description = "Tags comunes aplicados a todos los recursos"
  type        = map(string)
  default     = {}
}

variable "create_cross_region_replica" {
  description = "Si es true, crea una réplica cross-region de Oracle RDS en us-west-2 (Requisito 10.2)"
  type        = bool
  default     = true
}
