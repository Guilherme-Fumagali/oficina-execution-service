variable "aws_region" {
  description = "Região da AWS."
  type        = string
  default     = "us-east-1"
}

variable "ambiente" {
  description = "Ambiente lógico (staging ou prod)."
  type        = string
  default     = "staging"

  validation {
    condition     = contains(["staging", "prod"], var.ambiente)
    error_message = "ambiente deve ser staging ou prod."
  }
}

variable "docdb_instance_class" {
  description = "Classe da instância do DocumentDB."
  type        = string
  default     = "db.t4g.medium"
}

variable "docdb_database" {
  description = "Banco usado pelo Execution Service no cluster."
  type        = string
  default     = "execucao"
}

variable "docdb_username" {
  description = "Usuário administrador do cluster DocumentDB."
  type        = string
  default     = "oficina"
}
