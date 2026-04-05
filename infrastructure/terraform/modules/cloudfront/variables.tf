variable "nombre_entorno" {
  description = "Nombre del entorno (ej: prod, staging)"
  type        = string
}

variable "alb_dns_name" {
  description = "Nombre DNS del ALB que actúa como origen de CloudFront"
  type        = string
}

variable "alb_zone_id" {
  description = "Zone ID del ALB (para registros Route 53 alias)"
  type        = string
}

variable "waf_acl_arn" {
  description = "ARN del Web ACL WAF (scope CLOUDFRONT, debe estar en us-east-1)"
  type        = string
}

variable "certificate_arn" {
  description = "ARN del certificado ACM en us-east-1 para CloudFront"
  type        = string
}

variable "tags_comunes" {
  description = "Tags comunes aplicados a todos los recursos"
  type        = map(string)
  default     = {}
}
