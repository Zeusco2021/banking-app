variable "nombre_entorno" {
  description = "Nombre del entorno (ej: prod, staging)"
  type        = string
}

variable "scope" {
  description = "Scope del WAF: REGIONAL (para ALB/API Gateway) o CLOUDFRONT"
  type        = string
  default     = "REGIONAL"

  validation {
    condition     = contains(["REGIONAL", "CLOUDFRONT"], var.scope)
    error_message = "El scope debe ser REGIONAL o CLOUDFRONT."
  }
}

variable "tags_comunes" {
  description = "Tags comunes aplicados a todos los recursos"
  type        = map(string)
  default     = {}
}
