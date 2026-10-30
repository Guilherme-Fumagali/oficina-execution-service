package com.oficina.execution.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties("app.mensageria")
public record PropriedadesMensageria(Map<String, String> filas, String dlq, Relay relay) {

    public record Relay(int lote, Duration reserva) {}

    public String nomeDaFila(String chave) {
        var nome = filas.get(chave);
        if (nome == null || nome.isBlank()) {
            throw new IllegalStateException("Fila não configurada: " + chave);
        }
        return nome;
    }
}
