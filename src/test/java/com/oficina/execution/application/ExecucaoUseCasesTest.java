package com.oficina.execution.application;

import com.oficina.execution.application.evento.DiagnosticoConcluido;
import com.oficina.execution.application.evento.DiagnosticoIniciado;
import com.oficina.execution.application.evento.EventoIntegracao;
import com.oficina.execution.application.evento.ExecucaoFalhou;
import com.oficina.execution.application.evento.ExecucaoFinalizada;
import com.oficina.execution.application.evento.ReparoIniciado;
import com.oficina.execution.application.port.PublicadorEventos;
import com.oficina.execution.application.usecase.execucao.AlterarPrioridadeUseCase;
import com.oficina.execution.application.usecase.execucao.ConcluirDiagnosticoUseCase;
import com.oficina.execution.application.usecase.execucao.ConcluirReparoUseCase;
import com.oficina.execution.application.usecase.execucao.ConsultarExecucaoUseCase;
import com.oficina.execution.application.usecase.execucao.ConsultarFilaUseCase;
import com.oficina.execution.application.usecase.execucao.IniciarDiagnosticoUseCase;
import com.oficina.execution.application.usecase.execucao.IniciarReparoUseCase;
import com.oficina.execution.application.usecase.execucao.RegistrarApontamentoUseCase;
import com.oficina.execution.application.usecase.execucao.RegistrarFalhaUseCase;
import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.exception.RecursoNaoEncontradoException;
import com.oficina.execution.domain.exception.TransicaoInvalidaException;
import com.oficina.execution.domain.repository.ExecucaoRepository;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.oficina.execution.ExecucaoFixture.MECANICO;
import static com.oficina.execution.ExecucaoFixture.em;
import static com.oficina.execution.domain.valueobject.StatusExecucao.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecucaoUseCasesTest {

    private static final Instant AGORA = Instant.parse("2026-10-31T11:00:00Z");

    @Mock ExecucaoRepository repository;
    @Mock PublicadorEventos publicador;

    private final Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
    private final UnidadeDeTrabalhoImediata unidade = new UnidadeDeTrabalhoImediata();

    private Execucao existente(StatusExecucao status) {
        var execucao = em(status);
        when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));
        return execucao;
    }

    private void verificarEvento(Execucao execucao, EventoIntegracao evento) {
        verify(repository).salvar(execucao);
        verify(publicador).publicar(execucao.getOsId(), execucao.getSagaId(), null, evento);
        assertThat(unidade.execucoes).isEqualTo(1);
    }

    @Test
    void iniciarDiagnosticoPublicaDiagnosticoIniciado() {
        var execucao = existente(AGUARDANDO_DIAGNOSTICO);
        var mecanico = UUID.randomUUID();

        var resultado = new IniciarDiagnosticoUseCase(repository, publicador, unidade, relogio)
            .executar(execucao.getOsId(), mecanico);

        assertThat(resultado.getStatus()).isEqualTo(EM_DIAGNOSTICO);
        verificarEvento(execucao, new DiagnosticoIniciado(mecanico, AGORA));
    }

    @Test
    void concluirDiagnosticoPublicaDiagnosticoConcluido() {
        var execucao = existente(EM_DIAGNOSTICO);

        new ConcluirDiagnosticoUseCase(repository, publicador, unidade, relogio)
            .executar(execucao.getOsId(), MECANICO, "Troca de pastilhas");

        assertThat(execucao.getStatus()).isEqualTo(DIAGNOSTICO_CONCLUIDO);
        verificarEvento(execucao, new DiagnosticoConcluido(MECANICO, AGORA, "Troca de pastilhas"));
    }

    @Test
    void iniciarReparoPublicaReparoIniciado() {
        var execucao = existente(AGUARDANDO_REPARO);

        new IniciarReparoUseCase(repository, publicador, unidade, relogio).executar(execucao.getOsId(), MECANICO);

        assertThat(execucao.getStatus()).isEqualTo(EM_REPARO);
        verificarEvento(execucao, new ReparoIniciado(MECANICO, AGORA));
    }

    @Test
    void concluirReparoPublicaExecucaoFinalizada() {
        var execucao = existente(EM_REPARO);

        new ConcluirReparoUseCase(repository, publicador, unidade, relogio).executar(execucao.getOsId(), MECANICO);

        assertThat(execucao.getStatus()).isEqualTo(FINALIZADA);
        verificarEvento(execucao, new ExecucaoFinalizada(MECANICO, AGORA));
    }

    @Test
    void registrarFalhaPublicaExecucaoFalhou() {
        var execucao = existente(EM_REPARO);

        new RegistrarFalhaUseCase(repository, publicador, unidade, relogio)
            .executar(execucao.getOsId(), MECANICO, "Peça indisponível");

        assertThat(execucao.getStatus()).isEqualTo(FALHOU);
        verificarEvento(execucao, new ExecucaoFalhou(MECANICO, "Peça indisponível", AGORA));
    }

    @Test
    void transicaoInvalidaNaoGravaNemPublica() {
        var execucao = existente(AGUARDANDO_DIAGNOSTICO);
        var useCase = new ConcluirReparoUseCase(repository, publicador, unidade, relogio);
        var osId = execucao.getOsId();

        assertThatThrownBy(() -> useCase.executar(osId, MECANICO)).isInstanceOf(TransicaoInvalidaException.class);

        verify(repository, never()).salvar(any());
        verify(publicador, never()).publicar(any(), any(), any(), any());
    }

    @Test
    void execucaoInexistenteGeraRecursoNaoEncontrado() {
        var osId = UUID.randomUUID();
        when(repository.buscarPorId(osId)).thenReturn(Optional.empty());
        var useCase = new IniciarDiagnosticoUseCase(repository, publicador, unidade, relogio);

        assertThatThrownBy(() -> useCase.executar(osId, MECANICO))
            .isInstanceOf(RecursoNaoEncontradoException.class)
            .hasMessage("Execução não encontrada: " + osId);
    }

    @Test
    void registrarApontamentoNaoPublicaEvento() {
        var execucao = existente(EM_REPARO);
        when(repository.salvar(execucao)).thenReturn(execucao);

        var resultado = new RegistrarApontamentoUseCase(repository, unidade, relogio)
            .executar(execucao.getOsId(), MECANICO, "Aguardando peça do fornecedor");

        assertThat(resultado.getApontamentos()).last()
            .satisfies(a -> {
                assertThat(a.texto()).isEqualTo("Aguardando peça do fornecedor");
                assertThat(a.registradoEm()).isEqualTo(AGORA);
            });
        verify(publicador, never()).publicar(any(), any(), any(), any());
    }

    @Test
    void registrarApontamentoEmExecucaoInexistente() {
        var osId = UUID.randomUUID();
        when(repository.buscarPorId(osId)).thenReturn(Optional.empty());
        var useCase = new RegistrarApontamentoUseCase(repository, unidade, relogio);

        assertThatThrownBy(() -> useCase.executar(osId, MECANICO, "texto"))
            .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void alterarPrioridadeGravaNovaPrioridade() {
        var execucao = existente(AGUARDANDO_DIAGNOSTICO);
        when(repository.salvar(execucao)).thenReturn(execucao);

        var resultado = new AlterarPrioridadeUseCase(repository, unidade).executar(execucao.getOsId(), 8);

        assertThat(resultado.getPrioridade()).isEqualTo(8);
        assertThat(unidade.execucoes).isEqualTo(1);
    }

    @Test
    void alterarPrioridadeEmExecucaoInexistente() {
        var osId = UUID.randomUUID();
        when(repository.buscarPorId(osId)).thenReturn(Optional.empty());
        var useCase = new AlterarPrioridadeUseCase(repository, unidade);

        assertThatThrownBy(() -> useCase.executar(osId, 1)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void consultarFilaRepassaFiltros() {
        var fila = List.of(em(AGUARDANDO_REPARO));
        when(repository.listarFila(AGUARDANDO_REPARO, TipoEtapa.REPARO)).thenReturn(fila);

        assertThat(new ConsultarFilaUseCase(repository).executar(AGUARDANDO_REPARO, TipoEtapa.REPARO))
            .isSameAs(fila);
    }

    @Test
    void funcionarioConsultaQualquerExecucao() {
        var execucao = existente(EM_REPARO);

        assertThat(new ConsultarExecucaoUseCase(repository).executar(execucao.getOsId())).isSameAs(execucao);
    }

    @Test
    void clienteConsultaApenasAPropriaExecucao() {
        var execucao = existente(EM_REPARO);
        var useCase = new ConsultarExecucaoUseCase(repository);
        var osId = execucao.getOsId();
        var outroCliente = UUID.randomUUID();

        assertThat(useCase.executarDoCliente(osId, execucao.getClienteId())).isSameAs(execucao);
        assertThatThrownBy(() -> useCase.executarDoCliente(osId, outroCliente))
            .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void consultaDeExecucaoInexistente() {
        var osId = UUID.randomUUID();
        when(repository.buscarPorId(osId)).thenReturn(Optional.empty());
        var useCase = new ConsultarExecucaoUseCase(repository);

        assertThatThrownBy(() -> useCase.executar(osId)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
