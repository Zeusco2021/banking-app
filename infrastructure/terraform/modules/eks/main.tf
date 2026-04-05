# ============================================================
# Módulo Terraform: Amazon EKS con Managed Node Groups
# Requisitos 9.1, 9.2, 9.3, 9.4: escalabilidad automática
# ============================================================

terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = ">= 4.0"
    }
  }
}

# -------------------------------------------------------
# IAM Role para el plano de control de EKS
# -------------------------------------------------------
resource "aws_iam_role" "eks_cluster" {
  name = "${var.nombre_entorno}-eks-cluster-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-eks-cluster-role"
  })
}

resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  role       = aws_iam_role.eks_cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

# -------------------------------------------------------
# IAM Role para los nodos EKS (Managed Node Groups)
# -------------------------------------------------------
resource "aws_iam_role" "eks_node_group" {
  name = "${var.nombre_entorno}-eks-node-group-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-eks-node-group-role"
  })
}

resource "aws_iam_role_policy_attachment" "eks_worker_node_policy" {
  role       = aws_iam_role.eks_node_group.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "eks_cni_policy" {
  role       = aws_iam_role.eks_node_group.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "eks_ecr_readonly" {
  role       = aws_iam_role.eks_node_group.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# -------------------------------------------------------
# Security Group para los nodos EKS
# -------------------------------------------------------
resource "aws_security_group" "eks_nodes" {
  name        = "${var.nombre_entorno}-eks-nodes-sg"
  description = "Security group para nodos EKS — permite tráfico interno y desde ALB"
  vpc_id      = var.vpc_id

  ingress {
    description = "Tráfico interno entre nodos EKS"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-eks-nodes-sg"
  })
}

# -------------------------------------------------------
# Cluster EKS con OIDC habilitado para IRSA
# Requisito 9.1: escalabilidad horizontal de pods (HPA)
# -------------------------------------------------------
resource "aws_eks_cluster" "principal" {
  name     = "${var.nombre_entorno}-eks"
  role_arn = aws_iam_role.eks_cluster.arn
  version  = var.kubernetes_version

  vpc_config {
    subnet_ids              = var.subnet_ids_privadas
    security_group_ids      = [aws_security_group.eks_nodes.id]
    endpoint_private_access = true
    endpoint_public_access  = true
  }

  # Habilitar logs del plano de control
  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-eks"
    Requisito = "9.1,9.2,9.3,9.4"
  })

  depends_on = [aws_iam_role_policy_attachment.eks_cluster_policy]
}

# -------------------------------------------------------
# OIDC Provider para IRSA (IAM Roles for Service Accounts)
# Necesario para Cluster Autoscaler y otros componentes
# -------------------------------------------------------
data "tls_certificate" "eks_oidc" {
  url = aws_eks_cluster.principal.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks_oidc.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.principal.identity[0].oidc[0].issuer

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-eks-oidc"
  })
}

# -------------------------------------------------------
# IAM Role para Cluster Autoscaler (IRSA)
# Requisito 9.3: escalar nodos EKS automáticamente
# -------------------------------------------------------
data "aws_iam_policy_document" "cluster_autoscaler_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.eks.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "${replace(aws_iam_openid_connect_provider.eks.url, "https://", "")}:sub"
      values   = ["system:serviceaccount:kube-system:cluster-autoscaler"]
    }
  }
}

resource "aws_iam_role" "cluster_autoscaler" {
  name               = "${var.nombre_entorno}-cluster-autoscaler-role"
  assume_role_policy = data.aws_iam_policy_document.cluster_autoscaler_assume.json

  tags = merge(var.tags_comunes, {
    Nombre    = "${var.nombre_entorno}-cluster-autoscaler-role"
    Requisito = "9.3"
  })
}

resource "aws_iam_policy" "cluster_autoscaler" {
  name        = "${var.nombre_entorno}-cluster-autoscaler-policy"
  description = "Política para Cluster Autoscaler — gestión de Auto Scaling Groups"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "autoscaling:DescribeAutoScalingGroups",
          "autoscaling:DescribeAutoScalingInstances",
          "autoscaling:DescribeLaunchConfigurations",
          "autoscaling:DescribeScalingActivities",
          "autoscaling:DescribeTags",
          "ec2:DescribeImages",
          "ec2:DescribeInstanceTypes",
          "ec2:DescribeLaunchTemplateVersions",
          "ec2:GetInstanceTypesFromInstanceRequirements",
          "eks:DescribeNodegroup"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "autoscaling:SetDesiredCapacity",
          "autoscaling:TerminateInstanceInAutoScalingGroup"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "cluster_autoscaler" {
  role       = aws_iam_role.cluster_autoscaler.name
  policy_arn = aws_iam_policy.cluster_autoscaler.arn
}

# -------------------------------------------------------
# Add-ons de EKS: VPC CNI, CoreDNS, kube-proxy
# -------------------------------------------------------
resource "aws_eks_addon" "vpc_cni" {
  cluster_name = aws_eks_cluster.principal.name
  addon_name   = "vpc-cni"

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-eks-addon-vpc-cni"
  })
}

resource "aws_eks_addon" "coredns" {
  cluster_name = aws_eks_cluster.principal.name
  addon_name   = "coredns"

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-eks-addon-coredns"
  })

  depends_on = [aws_eks_node_group.tier_0_10k]
}

resource "aws_eks_addon" "kube_proxy" {
  cluster_name = aws_eks_cluster.principal.name
  addon_name   = "kube-proxy"

  tags = merge(var.tags_comunes, {
    Nombre = "${var.nombre_entorno}-eks-addon-kube-proxy"
  })
}

# -------------------------------------------------------
# Managed Node Group — Tier 0-10K usuarios
# Requisito 9.4: mínimo 2 réplicas, 3 nodos EKS
# -------------------------------------------------------
resource "aws_eks_node_group" "tier_0_10k" {
  cluster_name    = aws_eks_cluster.principal.name
  node_group_name = "${var.nombre_entorno}-ng-tier-0-10k"
  node_role_arn   = aws_iam_role.eks_node_group.arn
  subnet_ids      = var.subnet_ids_privadas
  instance_types  = [var.node_instance_type]

  scaling_config {
    desired_size = 2
    min_size     = 2
    max_size     = 10
  }

  update_config {
    max_unavailable = 1
  }

  tags = merge(var.tags_comunes, {
    Nombre                                                = "${var.nombre_entorno}-ng-tier-0-10k"
    Tier                                                  = "0-10K"
    Requisito                                             = "9.4"
    "k8s.io/cluster-autoscaler/enabled"                   = "true"
    "k8s.io/cluster-autoscaler/${var.nombre_entorno}-eks" = "owned"
  })

  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node_policy,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.eks_ecr_readonly,
  ]
}

# -------------------------------------------------------
# Managed Node Group — Tier 10K-100K usuarios
# Requisito 9.4: 5-10 réplicas, 10 nodos EKS
# -------------------------------------------------------
resource "aws_eks_node_group" "tier_10k_100k" {
  cluster_name    = aws_eks_cluster.principal.name
  node_group_name = "${var.nombre_entorno}-ng-tier-10k-100k"
  node_role_arn   = aws_iam_role.eks_node_group.arn
  subnet_ids      = var.subnet_ids_privadas
  instance_types  = [var.node_instance_type]

  scaling_config {
    desired_size = 5
    min_size     = 5
    max_size     = 20
  }

  update_config {
    max_unavailable = 1
  }

  tags = merge(var.tags_comunes, {
    Nombre                                                = "${var.nombre_entorno}-ng-tier-10k-100k"
    Tier                                                  = "10K-100K"
    Requisito                                             = "9.4"
    "k8s.io/cluster-autoscaler/enabled"                   = "true"
    "k8s.io/cluster-autoscaler/${var.nombre_entorno}-eks" = "owned"
  })

  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node_policy,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.eks_ecr_readonly,
  ]
}

# -------------------------------------------------------
# Managed Node Group — Tier 100K-1M usuarios
# Requisito 9.4: 20+ nodos EKS con KEDA activo
# -------------------------------------------------------
resource "aws_eks_node_group" "tier_100k_1m" {
  cluster_name    = aws_eks_cluster.principal.name
  node_group_name = "${var.nombre_entorno}-ng-tier-100k-1m"
  node_role_arn   = aws_iam_role.eks_node_group.arn
  subnet_ids      = var.subnet_ids_privadas
  instance_types  = [var.node_instance_type]

  scaling_config {
    desired_size = 10
    min_size     = 10
    max_size     = 50
  }

  update_config {
    max_unavailable = 2
  }

  tags = merge(var.tags_comunes, {
    Nombre                                                = "${var.nombre_entorno}-ng-tier-100k-1m"
    Tier                                                  = "100K-1M"
    Requisito                                             = "9.4"
    "k8s.io/cluster-autoscaler/enabled"                   = "true"
    "k8s.io/cluster-autoscaler/${var.nombre_entorno}-eks" = "owned"
  })

  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node_policy,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.eks_ecr_readonly,
  ]
}

# -------------------------------------------------------
# Managed Node Group — Tier 1M-5M usuarios
# Requisito 9.4: configuración multi-región activa
# -------------------------------------------------------
resource "aws_eks_node_group" "tier_1m_5m" {
  cluster_name    = aws_eks_cluster.principal.name
  node_group_name = "${var.nombre_entorno}-ng-tier-1m-5m"
  node_role_arn   = aws_iam_role.eks_node_group.arn
  subnet_ids      = var.subnet_ids_privadas
  instance_types  = [var.node_instance_type]

  scaling_config {
    desired_size = 20
    min_size     = 20
    max_size     = 100
  }

  update_config {
    max_unavailable = 3
  }

  tags = merge(var.tags_comunes, {
    Nombre                                                = "${var.nombre_entorno}-ng-tier-1m-5m"
    Tier                                                  = "1M-5M"
    Requisito                                             = "9.4"
    "k8s.io/cluster-autoscaler/enabled"                   = "true"
    "k8s.io/cluster-autoscaler/${var.nombre_entorno}-eks" = "owned"
  })

  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node_policy,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.eks_ecr_readonly,
  ]
}
