# ============================================================
# Variables del módulo cloudwatch-alarms
# ============================================================

variable "nombre_entorno" {
  description = "Nombre del entorno (ej: prod, staging, dev)"
  type        = string
}

variable "nombre_cluster_ecs" {
  description = "Nombre del cluster ECS/EKS donde corren los microservicios"
  type        = string
  default     = "banco-plataforma-cluster"
}

variable "emails_operaciones" {
  description = "Lista de emails del equipo de operaciones para recibir alertas SNS"
  type        = list(string)
  default     = []
}

variable "tags_comunes" {
  description = "Tags comunes aplicados a todos los recursos del módulo"
  type        = map(string)
  default = {
    Proyecto   = "banco-plataforma-migracion"
    Gestionado = "terraform"
  }
}
