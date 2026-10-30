package com.oficina.execution.infrastructure.observability;

import com.oficina.execution.application.port.MetricasExecucao;
import com.oficina.execution.domain.entity.TransicaoExecucao;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MicrometerMetricasExecucao implements MetricasExecucao {

    private static final Logger log = LoggerFactory.getLogger(MicrometerMetricasExecucao.class);

    static final String TRANSICOES = "oficina.execucao.transicoes";
    static final String DURACAO = "oficina.execucao.duracao";
    static final String FALHAS = "oficina.execucao.falhas";
    static final String SEM_STATUS = "NENHUM";

    private final MeterRegistry registry;

    public MicrometerMetricasExecucao(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void registrarTransicao(TransicaoExecucao transicao) {
        var de = transicao.de() == null ? SEM_STATUS : transicao.de().name();
        var para = transicao.para().name();
        registry.counter(TRANSICOES, "de", de, "para", para).increment();
        if (transicao.duracaoEtapa() != null) {
            registry.timer(DURACAO, "etapa", transicao.etapa().name()).record(transicao.duracaoEtapa());
        }
        if (transicao.para() == StatusExecucao.FALHOU) {
            registry.counter(FALHAS).increment();
        }
        log.atInfo()
            .addKeyValue("os.id", transicao.osId())
            .addKeyValue("execucao.status_origem", de)
            .addKeyValue("execucao.status_destino", para)
            .log("Execução da OS {} passou de {} para {}", transicao.osId(), de, para);
    }
}
