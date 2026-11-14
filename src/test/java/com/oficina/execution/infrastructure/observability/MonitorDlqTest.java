package com.oficina.execution.infrastructure.observability;

import com.oficina.execution.infrastructure.messaging.PropriedadesMensageria;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorDlqTest {

    @Mock SqsAsyncClient sqs;

    @Test
    @SuppressWarnings("unchecked")
    void falhaNaConsultaMantemUltimoValor() {
        var registry = new SimpleMeterRegistry();
        var propriedades = new PropriedadesMensageria(Map.of(), "oficina-local-execution-comandos-dlq",
            new PropriedadesMensageria.Relay(50, Duration.ofSeconds(30)));
        when(sqs.getQueueUrl(any(Consumer.class)))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("sem rede")));
        var monitor = new MonitorDlq(sqs, propriedades, registry);

        monitor.atualizar();

        assertThat(monitor.mensagensNaDlq()).isZero();
        assertThat(registry.get("oficina.mensageria.dlq.mensagens")
            .tag("fila", "oficina-local-execution-comandos-dlq").gauge().value()).isZero();
    }
}
