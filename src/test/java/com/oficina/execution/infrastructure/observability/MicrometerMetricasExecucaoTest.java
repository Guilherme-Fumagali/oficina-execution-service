package com.oficina.execution.infrastructure.observability;

import com.oficina.execution.domain.entity.TransicaoExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.oficina.execution.domain.valueobject.StatusExecucao.*;
import static org.assertj.core.api.Assertions.assertThat;

class MicrometerMetricasExecucaoTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerMetricasExecucao metricas = new MicrometerMetricasExecucao(registry);
    private final UUID osId = UUID.randomUUID();

    @Test
    void contaTransicaoInicialSemStatusDeOrigem() {
        metricas.registrarTransicao(new TransicaoExecucao(osId, null, AGUARDANDO_DIAGNOSTICO, TipoEtapa.DIAGNOSTICO, null));

        assertThat(registry.get(MicrometerMetricasExecucao.TRANSICOES)
            .tags("de", "NENHUM", "para", "AGUARDANDO_DIAGNOSTICO").counter().count()).isEqualTo(1.0);
        assertThat(registry.find(MicrometerMetricasExecucao.DURACAO).timer()).isNull();
        assertThat(registry.find(MicrometerMetricasExecucao.FALHAS).counter()).isNull();
    }

    @Test
    void conclusaoDeEtapaRegistraDuracao() {
        metricas.registrarTransicao(new TransicaoExecucao(osId, EM_REPARO, FINALIZADA, TipoEtapa.REPARO, Duration.ofMinutes(45)));

        var timer = registry.get(MicrometerMetricasExecucao.DURACAO).tag("etapa", "REPARO").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MINUTES)).isEqualTo(45.0);
        assertThat(registry.get(MicrometerMetricasExecucao.TRANSICOES)
            .tags("de", "EM_REPARO", "para", "FINALIZADA").counter().count()).isEqualTo(1.0);
    }

    @Test
    void falhaIncrementaContadorDeFalhas() {
        metricas.registrarTransicao(new TransicaoExecucao(osId, EM_REPARO, FALHOU, TipoEtapa.REPARO, null));
        metricas.registrarTransicao(new TransicaoExecucao(osId, AGUARDANDO_REPARO, FALHOU, TipoEtapa.REPARO, null));

        assertThat(registry.get(MicrometerMetricasExecucao.FALHAS).counter().count()).isEqualTo(2.0);
    }
}
