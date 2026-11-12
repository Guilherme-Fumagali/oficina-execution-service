locals {
  arn_fila_os_eventos = "arn:aws:sqs:${local.regiao}:${local.conta}:${local.fila_os_eventos}"
  arn_boundary_pod    = "arn:aws:iam::${local.conta}:policy/oficina-pod-boundary"
}

data "aws_iam_policy_document" "confianca_pod" {
  statement {
    sid     = "EksPodIdentity"
    actions = ["sts:AssumeRole", "sts:TagSession"]

    principals {
      type        = "Service"
      identifiers = ["pods.eks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "pod" {
  name                 = "oficina-execution-pod-${var.ambiente}"
  description          = "Papel do pod execution-service via EKS Pod Identity"
  assume_role_policy   = data.aws_iam_policy_document.confianca_pod.json
  permissions_boundary = local.arn_boundary_pod
}

data "aws_iam_policy_document" "filas_pod" {
  statement {
    sid = "ConsumirComandos"
    actions = [
      "sqs:ChangeMessageVisibility",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
      "sqs:ReceiveMessage",
    ]
    resources = [aws_sqs_queue.comandos.arn]
  }

  statement {
    sid       = "MedirDlq"
    actions   = ["sqs:GetQueueAttributes", "sqs:GetQueueUrl"]
    resources = [aws_sqs_queue.comandos_dlq.arn]
  }

  statement {
    sid       = "PublicarEventosParaOs"
    actions   = ["sqs:GetQueueUrl", "sqs:SendMessage"]
    resources = [local.arn_fila_os_eventos]
  }
}

resource "aws_iam_role_policy" "filas_pod" {
  name   = "filas-execution-service"
  role   = aws_iam_role.pod.id
  policy = data.aws_iam_policy_document.filas_pod.json
}

resource "aws_eks_pod_identity_association" "execution" {
  cluster_name    = data.aws_ssm_parameter.eks_cluster_name.value
  namespace       = "oficina"
  service_account = "execution-service"
  role_arn        = aws_iam_role.pod.arn
}
