package com.oficina.execution.application;

import com.oficina.execution.application.comando.ComandoRecebido;
import com.oficina.execution.application.comando.DadosComando;
import com.oficina.execution.application.comando.RespostaComandos;
import com.oficina.execution.application.comando.ResultadoProcessamento;
import com.oficina.execution.application.evento.ComandoRejeitado;
import com.oficina.execution.application.port.PublicadorEventos;
import com.oficina.execution.application.port.RegistroMensagensProcessadas;
import com.oficina.execution.application.usecase.comando.CancelarExecucaoUseCase;
import com.oficina.execution.application.usecase.comando.EnfileirarDiagnosticoUseCase;
import com.oficina.execution.application.usecase.comando.EnfileirarReparoUseCase;
import com.oficina.execution.application.usecase.comando.ProcessarComandoUseCase;
import com.oficina.execution.application.usecase.comando.ReabrirDiagnosticoUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.oficina.execution.ExecucaoFixture.T0;
import static com.oficina.execution.ExecucaoFixture.itens;
import static com.oficina.execution.ExecucaoFixture.veiculo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessarComandoUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-10-31T10:00:00Z");

    @Mock RegistroMensagensProcessadas mensagensProcessadas;
    @Mock EnfileirarDiagnosticoUseCase enfileirarDiagnostico;
    @Mock ReabrirDiagnosticoUseCase reabrirDiagnostico;
    @Mock EnfileirarReparoUseCase enfileirarReparo;
    @Mock CancelarExecucaoUseCase cancelarExecucao;
    @Mock PublicadorEventos publicador;

    private final UnidadeDeTrabalhoImediata unidade = new UnidadeDeTrabalhoImediata();
    private ProcessarComandoUseCase useCase;

    @BeforeEach
    void configurar() {
        useCase = new ProcessarComandoUseCase(mensagensProcessadas, enfileirarDiagnostico, reabrirDiagnostico,
            enfileirarReparo, cancelarExecucao, new RespostaComandos(publicador), unidade,
            Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private ComandoRecebido comando(String tipo, DadosComando dados) {
        return new ComandoRecebido(UUID.randomUUID(), tipo, UUID.randomUUID(), UUID.randomUUID(), dados);
    }

    @Test
    void mensagemJaProcessadaEDescartada() {
        var comando = comando("IniciarDiagnostico", new DadosComando.IniciarDiagnostico(UUID.randomUUID(), veiculo(), T0));
        when(mensagensProcessadas.jaProcessada(comando.mensagemId())).thenReturn(true);

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoProcessamento.DUPLICADA);

        verify(mensagensProcessadas, never()).registrar(any(), any(), any());
        verifyNoInteractions(enfileirarDiagnostico, publicador);
    }

    @Test
    void registraMensagemEDelegaIniciarDiagnostico() {
        var dados = new DadosComando.IniciarDiagnostico(UUID.randomUUID(), veiculo(), T0);
        var comando = comando("IniciarDiagnostico", dados);
        when(enfileirarDiagnostico.executar(comando, dados)).thenReturn(ResultadoProcessamento.APLICADA);

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoProcessamento.APLICADA);

        verify(mensagensProcessadas).registrar(comando.mensagemId(), "IniciarDiagnostico", AGORA);
        assertThat(unidade.execucoes).isEqualTo(1);
    }

    @Test
    void delegaReabrirDiagnostico() {
        var dados = new DadosComando.ReabrirDiagnostico("sem itens");
        var comando = comando("ReabrirDiagnostico", dados);
        when(reabrirDiagnostico.executar(comando, dados)).thenReturn(ResultadoProcessamento.APLICADA);

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoProcessamento.APLICADA);
    }

    @Test
    void delegaIniciarReparo() {
        var dados = new DadosComando.IniciarReparo(itens());
        var comando = comando("IniciarReparo", dados);
        when(enfileirarReparo.executar(comando, dados)).thenReturn(ResultadoProcessamento.IGNORADA);

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoProcessamento.IGNORADA);
    }

    @Test
    void delegaCancelarExecucao() {
        var dados = new DadosComando.CancelarExecucao("reprovado");
        var comando = comando("CancelarExecucao", dados);
        when(cancelarExecucao.executar(comando, dados)).thenReturn(ResultadoProcessamento.REJEITADA);

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoProcessamento.REJEITADA);
    }

    @Test
    void comandoDesconhecidoERejeitado() {
        var comando = comando("GerarOrcamento", new DadosComando.NaoReconhecido());

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoProcessamento.REJEITADA);

        verify(publicador).publicar(eq(comando.osId()), eq(comando.sagaId()), eq(comando.mensagemId()),
            eq(new ComandoRejeitado("GerarOrcamento", "Comando não reconhecido: GerarOrcamento.", "COMANDO_DESCONHECIDO")));
        verify(mensagensProcessadas).registrar(comando.mensagemId(), "GerarOrcamento", AGORA);
    }

    @Test
    void dadosInvalidosSaoRejeitados() {
        var comando = comando("IniciarReparo", new DadosComando.Invalido("quantidade inválida"));

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoProcessamento.REJEITADA);

        verify(publicador).publicar(eq(comando.osId()), eq(comando.sagaId()), eq(comando.mensagemId()),
            eq(new ComandoRejeitado("IniciarReparo", "quantidade inválida", "DADOS_INVALIDOS")));
    }

    @Test
    void falhaTecnicaPropagaParaNovaTentativa() {
        var dados = new DadosComando.CancelarExecucao("x");
        var comando = comando("CancelarExecucao", dados);
        when(cancelarExecucao.executar(comando, dados)).thenThrow(new IllegalStateException("banco indisponível"));

        assertThatThrownBy(() -> useCase.executar(comando)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resultadoTemRotuloDaMetrica() {
        assertThat(List.of(ResultadoProcessamento.values()))
            .extracting(ResultadoProcessamento::rotulo)
            .containsExactly("aplicada", "ignorada", "duplicada", "rejeitada");
    }
}
