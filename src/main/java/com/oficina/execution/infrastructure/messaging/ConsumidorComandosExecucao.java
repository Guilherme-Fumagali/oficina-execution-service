package com.oficina.execution.infrastructure.messaging;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class ConsumidorComandosExecucao {

    private final ProcessadorComandos processador;

    public ConsumidorComandosExecucao(ProcessadorComandos processador) {
        this.processador = processador;
    }

    @SqsListener(value = "${app.mensageria.filas.entrada}", maxConcurrentMessages = "5", maxMessagesPerPoll = "5",
        acknowledgementMode = "ON_SUCCESS")
    public void receber(Message<String> mensagem) {
        processador.processar(mensagem.getPayload(), mensagem.getHeaders());
    }
}
