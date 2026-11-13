package com.oficina.execution.infrastructure.messaging;

import com.oficina.execution.infrastructure.observability.MetricasMensageria;
import com.oficina.execution.infrastructure.persistence.document.MensagemOutboxDocument;
import io.awspring.cloud.sqs.operations.SqsOperations;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class EnvioMensagemOutboxTest {

    private static final Instant AGORA = Instant.parse("2026-10-31T12:00:00Z");

    @Mock SqsOperations sqs;
    @Mock RepositorioOutbox outbox;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PropriedadesMensageria propriedades = new PropriedadesMensageria(
        Map.of("os-eventos", "oficina-local-os-eventos"), "dlq",
        new PropriedadesMensageria.Relay(2, Duration.ofSeconds(30)));
    private final Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);

    private MensagemOutboxDocument mensagem(String fila) {
        return new MensagemOutboxDocument("id-1", fila, "ExecucaoEnfileirada", 1, "os-1", "saga-1", "{}",
            Map.of(), MensagemOutboxDocument.StatusPublicacao.PENDENTE, AGORA, AGORA, null, 0, null);
    }

    private EnvioMensagemOutbox envio() {
        return new EnvioMensagemOutbox(sqs, outbox, propriedades, new MetricasMensageria(registry), relogio);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicaEMarcaComoPublicada() {
        envio().enviar(mensagem("os-eventos"));

        var captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqs).send(captor.capture());
        verify(outbox).marcarPublicada("id-1", AGORA);
        assertThat(registry.get("oficina.mensageria.publicadas").tag("tipo", "ExecucaoEnfileirada").counter().count())
            .isEqualTo(1.0);
    }

    @Test
    void falhaNoEnvioRegistraTentativaELiberaAMensagem() {
        when(sqs.send(any(Consumer.class))).thenThrow(new IllegalStateException("SQS indisponível"));

        envio().enviar(mensagem("os-eventos"));

        verify(outbox).registrarFalha(eq("id-1"), eq("IllegalStateException: SQS indisponível"), eq(AGORA));
        verify(outbox, never()).marcarPublicada(anyString(), any());
        assertThat(registry.find("oficina.mensageria.publicadas").counter()).isNull();
    }

    @Test
    void erroLongoETruncado() {
        when(sqs.send(any(Consumer.class))).thenThrow(new IllegalStateException("x".repeat(800)));

        envio().enviar(mensagem("os-eventos"));

        var captor = ArgumentCaptor.forClass(String.class);
        verify(outbox).registrarFalha(eq("id-1"), captor.capture(), eq(AGORA));
        assertThat(captor.getValue()).hasSize(500);
    }

    @Test
    void filaNaoConfiguradaContaComoFalha() {
        envio().enviar(mensagem("billing-comandos"));

        verify(outbox).registrarFalha(eq("id-1"), eq("IllegalStateException: Fila não configurada: billing-comandos"), eq(AGORA));
    }

    @Test
    void relayEnviaAteOLimiteDoLote() {
        when(outbox.reivindicarProxima(eq(AGORA), eq(AGORA.plusSeconds(30))))
            .thenReturn(Optional.of(mensagem("os-eventos")));
        var relay = new RelayOutbox(outbox, envio(), propriedades, relogio);

        relay.publicarPendentes();

        verify(outbox, times(2)).reivindicarProxima(AGORA, AGORA.plusSeconds(30));
        verify(outbox, times(2)).marcarPublicada("id-1", AGORA);
    }

    @Test
    void relayParaQuandoNaoHaPendentes() {
        when(outbox.reivindicarProxima(any(), any())).thenReturn(Optional.empty());
        var relay = new RelayOutbox(outbox, envio(), propriedades, relogio);

        relay.publicarPendentes();

        verify(outbox, times(1)).reivindicarProxima(any(), any());
        verify(sqs, never()).send(any(Consumer.class));
    }
}
