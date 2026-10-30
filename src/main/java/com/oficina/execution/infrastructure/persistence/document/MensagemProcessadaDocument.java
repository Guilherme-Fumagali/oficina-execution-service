package com.oficina.execution.infrastructure.persistence.document;

import com.oficina.execution.infrastructure.persistence.ColecoesMongo;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = ColecoesMongo.MENSAGENS_PROCESSADAS)
public record MensagemProcessadaDocument(@Id String mensagemId, String tipo, Instant processadaEm) {}
