locals {
  identificador_docdb = "oficina-execution-docdb-${var.ambiente}"
}

resource "random_password" "docdb" {
  length  = 32
  special = false
}

resource "aws_docdb_subnet_group" "execucao" {
  name        = local.identificador_docdb
  description = "Subnets privadas do DocumentDB do Execution Service"
  subnet_ids  = local.private_subnet_ids
}

resource "aws_security_group" "docdb" {
  name        = "${local.identificador_docdb}-sg"
  description = "DocumentDB acessivel apenas pelos nos do EKS"
  vpc_id      = local.vpc_id

  tags = { Name = "${local.identificador_docdb}-sg" }
}

resource "aws_vpc_security_group_ingress_rule" "docdb_do_eks" {
  security_group_id            = aws_security_group.docdb.id
  description                  = "DocumentDB a partir dos nos do EKS"
  from_port                    = 27017
  to_port                      = 27017
  ip_protocol                  = "tcp"
  referenced_security_group_id = data.aws_ssm_parameter.eks_node_security_group_id.value
}

resource "aws_docdb_cluster_parameter_group" "execucao" {
  name        = local.identificador_docdb
  family      = "docdb5.0"
  description = "Parametros do DocumentDB do Execution Service com TLS obrigatorio"

  parameter {
    name  = "tls"
    value = "enabled"
  }
}

resource "aws_docdb_cluster" "execucao" {
  cluster_identifier              = local.identificador_docdb
  engine                          = "docdb"
  engine_version                  = "5.0.0"
  master_username                 = var.docdb_username
  master_password                 = random_password.docdb.result
  port                            = 27017
  db_subnet_group_name            = aws_docdb_subnet_group.execucao.name
  db_cluster_parameter_group_name = aws_docdb_cluster_parameter_group.execucao.name
  vpc_security_group_ids          = [aws_security_group.docdb.id]

  storage_encrypted       = true
  backup_retention_period = 1
  skip_final_snapshot     = true
  deletion_protection     = false
  apply_immediately       = true
}

resource "aws_docdb_cluster_instance" "execucao" {
  identifier                 = "${local.identificador_docdb}-1"
  cluster_identifier         = aws_docdb_cluster.execucao.id
  instance_class             = var.docdb_instance_class
  auto_minor_version_upgrade = true
  apply_immediately          = true
}
