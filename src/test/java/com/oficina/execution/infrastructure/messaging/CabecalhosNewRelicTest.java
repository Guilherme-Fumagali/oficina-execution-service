package com.oficina.execution.infrastructure.messaging;

import com.newrelic.api.agent.HeaderType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CabecalhosNewRelicTest {

    @Test
    void copiaSomenteOsCabecalhosDeRastreamento() {
        var origem = new HashMap<String, Object>();
        origem.put("traceparent", "00-abc-def-01");
        origem.put("tracestate", "nr=1");
        origem.put("tipo", "IniciarDiagnostico");
        origem.put("newrelic", null);

        var cabecalhos = CabecalhosNewRelic.de(origem);

        assertThat(cabecalhos.getHeaderType()).isEqualTo(HeaderType.MESSAGE);
        assertThat(cabecalhos.comoMapa()).containsOnly(
            Map.entry("traceparent", "00-abc-def-01"), Map.entry("tracestate", "nr=1"));
        assertThat(cabecalhos.getHeaderNames()).containsExactly("traceparent", "tracestate");
        assertThat(cabecalhos.containsHeader("traceparent")).isTrue();
        assertThat(cabecalhos.containsHeader("tipo")).isFalse();
        assertThat(cabecalhos.getHeader("tracestate")).isEqualTo("nr=1");
        assertThat(cabecalhos.getHeaders("tracestate")).containsExactly("nr=1");
        assertThat(cabecalhos.getHeaders("newrelic")).isEmpty();
    }

    @Test
    void aceitaOrigemNulaEGravacaoPeloAgente() {
        var cabecalhos = CabecalhosNewRelic.de(null);
        assertThat(cabecalhos.comoMapa()).isEmpty();

        cabecalhos.setHeader("newrelic", "payload");
        cabecalhos.addHeader("traceparent", "00-1-2-01");

        assertThat(cabecalhos.comoMapa()).containsEntry("newrelic", "payload").containsEntry("traceparent", "00-1-2-01");
    }
}
