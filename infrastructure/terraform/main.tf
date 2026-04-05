# ============================================================
# Módulo Raíz Terraform — Plataforma Bancaria AWS
# Requisito 13.1: infraestructura completa como código con Terraform 1.6+
# ============================================================

terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }

  # Backend S3 — descomentar y configurar antes del primer apply
  # backend "s3" {
  #   bucket         = "<NOMBRE_BUCKET_TFSTATE>"
  #   key            = "plataforma-bancaria/terraform.tfstate"
  #   region         = "us-east-1"
  #   encrypt        = true
  #   dynamodb_table = "<NOMBRE_TABLA_LOCK>"
  # }
}

# -------------------------------------------------------
# Proveedor AWS — región primaria (us-east-1)
# -------------------------------------------------------
provider "aws" {
  region = var.region_primaria
}

# -------------------------------------------------------
# Proveedor AWS — región DR (us-west-2)
# Usado para réplica cross-region de Oracle y recursos DR
# -------------------------------------------------------
provider "aws" {
  alias  = "dr"
  region = var.region_dr
}

# ============================================================
# WAF REGIONAL — protección para ALB y API Gateway
# Requisito 12.4: reglas OWASP Top 10
# ============================================================
module "waf_regional" {
  source = "./modules/waf"

  nombre_entorno = var.nombre_entorno
  scope          = "REGIONAL"
  tags_comunes   = var.tags_comunes
}

# ============================================================
# WAF CLOUDFRONT — debe desplegarse en us-east-1
# Requisito 12.4: reglas OWASP Top 10 para CloudFront
# ============================================================
module "waf_cloudfront" {
  source = "./modules/waf"

  nombre_entorno = "${var.nombre_entorno}-cf"
  scope          = "CLOUDFRONT"
  tags_comunes   = var.tags_comunes
}

# ============================================================
# EKS — cluster Kubernetes con managed node groups
# Requisitos 9.1, 9.2, 9.3, 9.4
# ============================================================
module "eks" {
  source = "./modules/eks"

  nombre_entorno      = var.nombre_entorno
  vpc_id              = var.vpc_id
  subnet_ids_privadas = var.subnet_ids_privadas
  kubernetes_version  = var.kubernetes_version
  node_instance_type  = var.node_instance_type
  tags_comunes        = var.tags_comunes
}

# ============================================================
# Oracle RDS — base de datos principal con réplica cross-region
# Requisitos 10.1, 10.2, 12.5
# ============================================================
module "rds_oracle" {
  source = "./modules/rds-oracle"

  nombre_entorno         = var.nombre_entorno
  vpc_id                 = var.vpc_id
  subnet_ids_privadas    = var.subnet_ids_privadas
  security_group_ids_eks = [module.eks.cluster_security_group_id]
  instance_class         = var.oracle_instance_class
  tags_comunes           = var.tags_comunes

  providers = {
    aws    = aws
    aws.dr = aws.dr
  }
}

# ============================================================
# ElastiCache Redis — caché distribuida con cifrado
# Requisito 12.5: cifrado en reposo y en tránsito
# ============================================================
module "elasticache_redis" {
  source = "./modules/elasticache-redis"

  nombre_entorno         = var.nombre_entorno
  vpc_id                 = var.vpc_id
  subnet_ids_privadas    = var.subnet_ids_privadas
  security_group_ids_eks = [module.eks.cluster_security_group_id]
  node_type              = var.redis_node_type
  auth_token             = var.redis_auth_token
  tags_comunes           = var.tags_comunes
}

# ============================================================
# MSK Kafka — mensajería asíncrona
# Requisitos 4.4, 4.5, 10.6
# ============================================================
module "msk_kafka" {
  source = "./modules/msk-kafka"

  nombre_entorno         = var.nombre_entorno
  vpc_id                 = var.vpc_id
  subnet_ids_privadas    = var.subnet_ids_privadas
  security_group_ids_eks = [module.eks.cluster_security_group_id]
  broker_instance_type   = var.broker_instance_type
  tags_comunes           = var.tags_comunes
}

# ============================================================
# DocumentDB — almacenamiento de auditoría (MongoDB-compatible)
# Requisitos 6.1, 6.3, 12.5
# ============================================================
module "documentdb" {
  source = "./modules/documentdb"

  nombre_entorno         = var.nombre_entorno
  vpc_id                 = var.vpc_id
  subnet_ids_privadas    = var.subnet_ids_privadas
  security_group_ids_eks = [module.eks.cluster_security_group_id]
  instance_class         = var.documentdb_instance_class
  tags_comunes           = var.tags_comunes
}

# ============================================================
# ALB — balanceador de carga internet-facing
# ============================================================
module "alb" {
  source = "./modules/alb"

  nombre_entorno      = var.nombre_entorno
  vpc_id              = var.vpc_id
  subnet_ids_publicas = var.subnet_ids_publicas
  certificate_arn     = var.certificate_arn
  s3_logs_bucket      = var.s3_logs_bucket
  tags_comunes        = var.tags_comunes
}

# ============================================================
# CloudFront — CDN con ALB como origen
# Requisito 12.2: TLS 1.2 mínimo
# ============================================================
module "cloudfront" {
  source = "./modules/cloudfront"

  nombre_entorno  = var.nombre_entorno
  alb_dns_name    = module.alb.alb_dns_name
  alb_zone_id     = module.alb.alb_zone_id
  waf_acl_arn     = module.waf_cloudfront.waf_acl_arn
  certificate_arn = var.certificate_arn
  tags_comunes    = var.tags_comunes
}

# ============================================================
# API Gateway — punto de entrada único para APIs bancarias
# Requisitos 1.1, 1.4, 8.5
# ============================================================
module "api_gateway" {
  source = "./modules/api-gateway"

  nombre_entorno = var.nombre_entorno
  waf_acl_arn    = module.waf_regional.waf_acl_arn
  tags_comunes   = var.tags_comunes
}

# ============================================================
# ALB DR — balanceador de carga en región DR (us-west-2)
# Requisito 10.5: failover automático
# ============================================================
module "alb_dr" {
  source = "./modules/alb"

  providers = {
    aws = aws.dr
  }

  nombre_entorno      = "${var.nombre_entorno}-dr"
  vpc_id              = var.vpc_id_dr
  subnet_ids_publicas = var.subnet_ids_publicas_dr
  certificate_arn     = var.certificate_arn_dr
  s3_logs_bucket      = var.s3_logs_bucket
  tags_comunes        = var.tags_comunes
}

# ============================================================
# Route 53 — DNS con health checks y failover automático
# Requisito 10.5: failover hacia región DR
# ============================================================
module "route53" {
  source = "./modules/route53"

  nombre_entorno      = var.nombre_entorno
  domain_name         = var.domain_name
  primary_alb_dns     = module.cloudfront.distribution_domain_name
  primary_alb_zone_id = module.cloudfront.distribution_hosted_zone_id
  dr_alb_dns          = module.alb_dr.alb_dns_name
  dr_alb_zone_id      = module.alb_dr.alb_zone_id
  tags_comunes        = var.tags_comunes
}
