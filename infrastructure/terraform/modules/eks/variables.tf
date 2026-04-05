variable "nombre_entorno" {
  description = "Nombre del entorno (ej: prod, staging)"
  type        = string
}

variable "vpc_id" {
  description = "ID de la VPC donde se despliega el cluster EKS"
  type        = string
}

variable "subnet_ids_privadas" {
  description = "Lista de IDs de subnets privadas (una por AZ) para los nodos EKS"
  type        = list(string)
}

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

variable "tags_comunes" {
  description = "Tags comunes aplicados a todos los recursos"
  type        = map(string)
  default     = {}
}
