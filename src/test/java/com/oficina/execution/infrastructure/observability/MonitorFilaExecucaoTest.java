package com.oficina.execution.infrastructure.observability;

import com.oficina.execution.domain.repository.ExecucaoRepository;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorFilaExecucaoTest {

    @Mock ExecucaoRepository repository;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void publicaTamanhoDaFilaPorEtapa() {
        when(repository.contarPorStatus(StatusExecucao.AGUARDANDO_DIAGNOSTICO)).thenReturn(4L);
        when(repository.contarPorStatus(StatusExecucao.AGUARDANDO_REPARO)).thenReturn(2L);
        var monitor = new MonitorFilaExecucao(repository, registry);

        monitor.atualizar();

        assertThat(registry.get("oficina.execucao.fila.tamanho").tag("etapa", "DIAGNOSTICO").gauge().value()).isEqualTo(4.0);
        assertThat(registry.get("oficina.execucao.fila.tamanho").tag("etapa", "REPARO").gauge().value()).isEqualTo(2.0);
        assertThat(monitor.tamanho(TipoEtapa.REPARO)).isEqualTo(2L);
    }

    @Test
    void falhaNaConsultaMantemUltimaMedida() {
        when(repository.contarPorStatus(any())).thenThrow(new IllegalStateException("banco indisponível"));
        var monitor = new MonitorFilaExecucao(repository, registry);

        monitor.atualizar();

        assertThat(monitor.tamanho(TipoEtapa.DIAGNOSTICO)).isZero();
    }
}
