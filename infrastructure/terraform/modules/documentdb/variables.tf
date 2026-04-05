variable "nombre_entorno" {
  description = "Nombre del entorno (ej: prod, staging)"
  type        = string
}

variable "vpc_id" {
  description = "ID de la VPC donde se despliega DocumentDB"
  type        = string
}

variable "subnet_ids_privadas" {
  description = "Lista de IDs de subnets privadas para el subnet group de DocumentDB"
  type        = list(string)
}

variable "security_group_ids_eks" {
  description = "IDs de security groups de los nodos EKS que acceden a DocumentDB"
  type        = list(string)
}

variable "instance_class" {
  description = "Clase de instancia para las instancias DocumentDB (ej: db.r6g.large)"
  type        = string
  default     = "db.r6g.large"
}

variable "tags_comunes" {
  description = "Tags comunes aplicados a todos los recursos"
  type        = map(string)
  default     = {}
}
