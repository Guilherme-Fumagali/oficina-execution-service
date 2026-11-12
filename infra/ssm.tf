resource "aws_ssm_parameter" "docdb_endpoint" {
  name        = "/oficina/${var.ambiente}/execution/docdb-endpoint"
  description = "Endereco host:porta do cluster DocumentDB do Execution Service"
  type        = "String"
  value       = "${aws_docdb_cluster.execucao.endpoint}:${aws_docdb_cluster.execucao.port}"

  depends_on = [aws_docdb_cluster_instance.execucao]
}

resource "aws_ssm_parameter" "docdb_database" {
  name  = "/oficina/${var.ambiente}/execution/docdb-database"
  type  = "String"
  value = var.docdb_database
}

resource "aws_ssm_parameter" "docdb_username" {
  name  = "/oficina/${var.ambiente}/execution/docdb-username"
  type  = "String"
  value = var.docdb_username
}

resource "aws_ssm_parameter" "docdb_password" {
  name  = "/oficina/${var.ambiente}/execution/docdb-password"
  type  = "SecureString"
  value = random_password.docdb.result
}
