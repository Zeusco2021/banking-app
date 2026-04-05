variable "nombre_entorno" {
  description = "Nombre del entorno (ej: prod, staging)"
  type        = string
}

variable "vpc_id" {
  description = "ID de la VPC donde se despliega el cluster MSK"
  type        = string
}

variable "subnet_ids_privadas" {
  description = "Lista de IDs de subnets privadas (una por AZ) para los brokers MSK"
  type        = list(string)
}

variable "security_group_ids_eks" {
  description = "IDs de security groups de los nodos EKS que acceden a MSK"
  type        = list(string)
}

variable "broker_instance_type" {
  description = "Tipo de instancia para los brokers MSK (ej: kafka.m5.2xlarge)"
  type        = string
  default     = "kafka.m5.2xlarge"
}

variable "tags_comunes" {
  description = "Tags comunes aplicados a todos los recursos"
  type        = map(string)
  default     = {}
}
