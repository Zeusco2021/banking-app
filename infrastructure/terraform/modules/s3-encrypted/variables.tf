variable "nombre_entorno" {
  description = "Nombre del entorno (ej: prod, staging)"
  type        = string
}

variable "nombres_buckets" {
  description = "Lista de sufijos de nombres de buckets a crear (se prefija con nombre_entorno)"
  type        = list(string)
  default     = ["reportes", "backups", "artefactos-cicd", "auditoria"]
}

variable "force_destroy" {
  description = "Permitir destrucción del bucket aunque tenga objetos (solo para entornos no-prod)"
  type        = bool
  default     = false
}

variable "tags_comunes" {
  description = "Tags comunes aplicados a todos los recursos"
  type        = map(string)
  default     = {}
}
