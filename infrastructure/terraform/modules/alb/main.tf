# ============================================================
# Módulo Terraform: Application Load Balancer (internet-facing)
# HTTPS con ACM, redirección HTTP→HTTPS, access logs en S3
# ============================================================

terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }
}

# -------------------------------------------------------
# Security Group para el ALB
# Permite 80/443 desde internet, egreso hacia EKS
# -------------------------------------------------------
resource "aws_security_group" "alb" {
  name        = "${var.nombre_entorno}-alb-sg"
  description = "Security group para ALB — permite HTTP/HTTPS desde internet"
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTP desde internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS desde internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Egreso hacia nodos EKS"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-alb-sg"
  })
}

# -------------------------------------------------------
# Application Load Balancer internet-facing
# Access logs habilitados en S3
# -------------------------------------------------------
resource "aws_lb" "principal" {
  name               = "${var.nombre_entorno}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.subnet_ids_publicas

  # Access logs en S3 para auditoría
  access_logs {
    bucket  = var.s3_logs_bucket
    prefix  = "${var.nombre_entorno}-alb"
    enabled = true
  }

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-alb"
  })
}

# -------------------------------------------------------
# Target Group por defecto (los microservicios EKS se registran aquí)
# -------------------------------------------------------
resource "aws_lb_target_group" "default" {
  name     = "${var.nombre_entorno}-alb-tg"
  port     = 8080
  protocol = "HTTP"
  vpc_id   = var.vpc_id

  health_check {
    path                = "/actuator/health"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-alb-tg"
  })
}

# -------------------------------------------------------
# Listener HTTP en puerto 80 — redirige a HTTPS
# Requisito 12.2: TLS obligatorio en comunicaciones externas
# -------------------------------------------------------
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.principal.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# -------------------------------------------------------
# Listener HTTPS en puerto 443 con certificado ACM
# Política TLS 1.3 — Requisito 12.2
# -------------------------------------------------------
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.principal.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.default.arn
  }
}
