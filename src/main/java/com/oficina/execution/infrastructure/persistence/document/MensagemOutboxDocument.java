package com.oficina.execution.infrastructure.persistence.document;

import com.oficina.execution.infrastructure.persistence.ColecoesMongo;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = ColecoesMongo.OUTBOX)
public record MensagemOutboxDocument(
    @Id String id,
    String fila,
    String tipo,
    int versao,
    String osId,
    String payload,
    Map<String, String> cabecalhos,
    StatusPublicacao status,
    Instant criadaEm,
    Instant reservadaAte,
    Instant publicadaEm,
    int tentativas,
    String ultimoErro
) {

    public enum StatusPublicacao {
        PENDENTE,
        PUBLICADA
    }
}
