package com.oficina.execution.infrastructure.messaging;

import com.oficina.execution.application.comando.ResultadoProcessamento;
import com.oficina.execution.application.port.RegistroMensagensProcessadas;
import com.oficina.execution.application.usecase.comando.ProcessarComandoUseCase;
import com.oficina.execution.infrastructure.observability.MetricasMensageria;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessadorComandosTest {

    @Mock ProcessarComandoUseCase processarComando;
    @Mock RegistroMensagensProcessadas mensagensProcessadas;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final UUID mensagemId = UUID.randomUUID();
    private ProcessadorComandos processador;

    @BeforeEach
    void configurar() {
        processador = new ProcessadorComandos(new LeitorComandos(new SerializadorMensagens()), processarComando,
            mensagensProcessadas, new MetricasMensageria(registry));
    }

    private String comando(String tipo) {
        return """
            {"mensagemId":"%s","tipo":"%s","versao":1,"sagaId":null,"osId":"%s","dados":{"motivo":"x"}}
            """.formatted(mensagemId, tipo, UUID.randomUUID());
    }

    private double consumidas(String tipo, String resultado) {
        var contador = registry.find("oficina.mensageria.consumidas").tags("tipo", tipo, "resultado", resultado).counter();
        return contador == null ? 0 : contador.count();
    }

    @Test
    void registraResultadoELimpaOContexto() {
        when(processarComando.executar(any())).thenReturn(ResultadoProcessamento.APLICADA);

        processador.processar(comando("CancelarExecucao"), Map.of("traceparent", "00-1-2-01"));

        assertThat(consumidas("CancelarExecucao", "aplicada")).isEqualTo(1.0);
        assertThat(MDC.get("os.id")).isNull();
        assertThat(MDC.get("mensagem.id")).isNull();
    }

    @Test
    void duplicataDetectadaPelaChaveEDescartada() {
        when(processarComando.executar(any())).thenThrow(new DuplicateKeyException("E11000"));
        when(mensagensProcessadas.jaProcessada(mensagemId)).thenReturn(true);

        processador.processar(comando("CancelarExecucao"), Map.of());

        assertThat(consumidas("CancelarExecucao", "duplicada")).isEqualTo(1.0);
    }

    @Test
    void chaveDuplicadaDeOutraOrigemVoltaParaAFila() {
        when(processarComando.executar(any())).thenThrow(new DuplicateKeyException("E11000 execucoes"));
        when(mensagensProcessadas.jaProcessada(mensagemId)).thenReturn(false);
        var corpo = comando("IniciarDiagnostico");
        var cabecalhos = Map.<String, Object>of();

        assertThatThrownBy(() -> processador.processar(corpo, cabecalhos)).isInstanceOf(DuplicateKeyException.class);

        assertThat(consumidas("IniciarDiagnostico", "erro")).isEqualTo(1.0);
    }

    @Test
    void mensagemDuplicadaPelaInboxNaoGeraLogDeProcessamento() {
        when(processarComando.executar(any())).thenReturn(ResultadoProcessamento.DUPLICADA);

        processador.processar(comando("ReabrirDiagnostico"), Map.of());

        assertThat(consumidas("ReabrirDiagnostico", "duplicada")).isEqualTo(1.0);
    }

    @Test
    void corpoInvalidoContaComoErroEPropaga() {
        var cabecalhos = Map.<String, Object>of();

        assertThatThrownBy(() -> processador.processar("{", cabecalhos)).isInstanceOf(MensagemInvalidaException.class);

        assertThat(consumidas("desconhecido", "erro")).isEqualTo(1.0);
    }
}
