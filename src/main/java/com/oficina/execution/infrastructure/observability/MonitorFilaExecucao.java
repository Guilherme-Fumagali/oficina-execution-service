package com.oficina.execution.infrastructure.observability;

import com.oficina.execution.domain.repository.ExecucaoRepository;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MonitorFilaExecucao {

    private static final Logger log = LoggerFactory.getLogger(MonitorFilaExecucao.class);

    static final String FILA_TAMANHO = "oficina.execucao.fila.tamanho";
    private static final Map<TipoEtapa, StatusExecucao> AGUARDANDO = Map.of(
        TipoEtapa.DIAGNOSTICO, StatusExecucao.AGUARDANDO_DIAGNOSTICO,
        TipoEtapa.REPARO, StatusExecucao.AGUARDANDO_REPARO);

    private final ExecucaoRepository repository;
    private final Map<TipoEtapa, AtomicLong> tamanhos = new EnumMap<>(TipoEtapa.class);

    public MonitorFilaExecucao(ExecucaoRepository repository, MeterRegistry registry) {
        this.repository = repository;
        for (var etapa : TipoEtapa.values()) {
            var tamanho = new AtomicLong();
            tamanhos.put(etapa, tamanho);
            Gauge.builder(FILA_TAMANHO, tamanho, AtomicLong::get).tag("etapa", etapa.name()).register(registry);
        }
    }

    @Scheduled(fixedDelayString = "${app.metricas.fila-intervalo:30000}")
    public void atualizar() {
        try {
            AGUARDANDO.forEach((etapa, status) -> tamanhos.get(etapa).set(repository.contarPorStatus(status)));
        } catch (RuntimeException e) {
            log.warn("Não foi possível medir a fila de execução: {}", e.getMessage());
        }
    }

    public long tamanho(TipoEtapa etapa) {
        return tamanhos.get(etapa).get();
    }
}
