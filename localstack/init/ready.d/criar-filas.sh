#!/bin/bash
set -euo pipefail

criar_fila_com_dlq() {
  local fila="$1"
  local dlq="${fila}-dlq"
  local url_dlq arn_dlq atributos
  url_dlq=$(awslocal sqs create-queue --queue-name "$dlq" \
    --attributes MessageRetentionPeriod=1209600 --query QueueUrl --output text)
  arn_dlq=$(awslocal sqs get-queue-attributes --queue-url "$url_dlq" \
    --attribute-names QueueArn --query Attributes.QueueArn --output text)
  atributos=$(printf '{"VisibilityTimeout":"30","ReceiveMessageWaitTimeSeconds":"20","RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"5\\"}"}' "$arn_dlq")
  awslocal sqs create-queue --queue-name "$fila" --attributes "$atributos" > /dev/null
  echo "Fila ${fila} criada com DLQ ${dlq}"
}

criar_fila_com_dlq oficina-local-execution-comandos
criar_fila_com_dlq oficina-local-os-eventos
