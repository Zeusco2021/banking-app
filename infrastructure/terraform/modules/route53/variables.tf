variable "nombre_entorno" {
  description = "Nombre del entorno (ej: prod, staging)"
  type        = string
}

variable "domain_name" {
  description = "Nombre de dominio principal (ej: banco.example.com)"
  type        = string
}

variable "primary_alb_dns" {
  description = "Nombre DNS del ALB/CloudFront de la región primaria"
  type        = string
}

variable "primary_alb_zone_id" {
  description = "Zone ID del ALB/CloudFront de la región primaria"
  type        = string
}

variable "dr_alb_dns" {
  description = "Nombre DNS del ALB de la región DR (us-west-2)"
  type        = string
}

variable "dr_alb_zone_id" {
  description = "Zone ID del ALB de la región DR (us-west-2)"
  type        = string
}

variable "tags_comunes" {
  description = "Tags comunes aplicados a todos los recursos"
  type        = map(string)
  default     = {}
}
