package com.oficina.execution.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record MensagemEnvelope(
    UUID mensagemId,
    String tipo,
    int versao,
    UUID sagaId,
    UUID osId,
    UUID causadaPor,
    String origem,
    Instant ocorridoEm,
    JsonNode dados
) {}
