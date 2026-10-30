output "fila_comandos_url" {
  description = "URL da fila de comandos consumida pelo Execution Service."
  value       = aws_sqs_queue.comandos.url
}

output "fila_comandos_dlq_url" {
  description = "URL da DLQ da fila de comandos."
  value       = aws_sqs_queue.comandos_dlq.url
}

output "papel_pod_arn" {
  description = "ARN do papel assumido pelo pod via Pod Identity."
  value       = aws_iam_role.pod.arn
}

output "docdb_endpoint" {
  description = "Endereco do cluster DocumentDB, publicado tambem no SSM."
  value       = aws_ssm_parameter.docdb_endpoint.value
}
