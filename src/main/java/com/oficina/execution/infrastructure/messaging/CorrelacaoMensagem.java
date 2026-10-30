package com.oficina.execution.infrastructure.messaging;

import com.newrelic.api.agent.NewRelic;
import org.slf4j.MDC;

final class CorrelacaoMensagem {

    static final String OS_ID = "os.id";
    static final String SAGA_ID = "saga.id";
    static final String MENSAGEM_ID = "mensagem.id";
    static final String MENSAGEM_TIPO = "mensagem.tipo";

    private CorrelacaoMensagem() {}

    static void registrar(Object osId, Object sagaId, Object mensagemId, String tipo) {
        colocar(OS_ID, osId);
        colocar(SAGA_ID, sagaId);
        colocar(MENSAGEM_ID, mensagemId);
        NewRelic.addCustomParameter(MENSAGEM_TIPO, tipo);
    }

    static void limpar() {
        MDC.remove(OS_ID);
        MDC.remove(SAGA_ID);
        MDC.remove(MENSAGEM_ID);
    }

    private static void colocar(String chave, Object valor) {
        if (valor == null) {
            return;
        }
        MDC.put(chave, valor.toString());
        if (!MENSAGEM_ID.equals(chave)) {
            NewRelic.addCustomParameter(chave, valor.toString());
        }
    }
}
