locals {
  fila_comandos   = "oficina-${var.ambiente}-execution-comandos"
  fila_os_eventos = "oficina-${var.ambiente}-os-eventos"
}

resource "aws_sqs_queue" "comandos_dlq" {
  name                      = "${local.fila_comandos}-dlq"
  sqs_managed_sse_enabled   = true
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "comandos" {
  name                       = local.fila_comandos
  sqs_managed_sse_enabled    = true
  visibility_timeout_seconds = 30
  message_retention_seconds  = 345600
  receive_wait_time_seconds  = 20

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.comandos_dlq.arn
    maxReceiveCount     = 5
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "comandos_dlq" {
  queue_url = aws_sqs_queue.comandos_dlq.id

  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.comandos.arn]
  })
}
