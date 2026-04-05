variable "nombre_entorno" {
  description = "Nombre del entorno (ej: prod, staging)"
  type        = string
}

variable "waf_acl_arn" {
  description = "ARN del Web ACL WAF para asociar al API Gateway"
  type        = string
}

variable "tags_comunes" {
  description = "Tags comunes aplicados a todos los recursos"
  type        = map(string)
  default     = {}
}

# -------------------------------------------------------
# Stage-level throttling — Requisito 8.5
# -------------------------------------------------------
variable "stage_throttle_rate_limit" {
  description = "Throttling rate limit (requests/second) at the stage level — capa sobre Redis Rate Limiter"
  type        = number
  default     = 10000
}

variable "stage_throttle_burst_limit" {
  description = "Throttling burst limit (concurrent requests) at the stage level"
  type        = number
  default     = 5000
}
