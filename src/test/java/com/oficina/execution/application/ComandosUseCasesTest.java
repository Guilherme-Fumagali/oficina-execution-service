package com.oficina.execution.application;

import com.oficina.execution.application.comando.ComandoRecebido;
import com.oficina.execution.application.comando.DadosComando;
import com.oficina.execution.application.comando.RespostaComandos;
import com.oficina.execution.application.comando.ResultadoProcessamento;
import com.oficina.execution.application.evento.ComandoRejeitado;
import com.oficina.execution.application.evento.EventoIntegracao;
import com.oficina.execution.application.evento.ExecucaoCancelada;
import com.oficina.execution.application.evento.ExecucaoEnfileirada;
import com.oficina.execution.application.port.PublicadorEventos;
import com.oficina.execution.application.usecase.comando.CancelarExecucaoUseCase;
import com.oficina.execution.application.usecase.comando.EnfileirarDiagnosticoUseCase;
import com.oficina.execution.application.usecase.comando.EnfileirarReparoUseCase;
import com.oficina.execution.application.usecase.comando.ReabrirDiagnosticoUseCase;
import com.oficina.execution.domain.entity.DadosExecucao;
import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.repository.ExecucaoRepository;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.oficina.execution.ExecucaoFixture.T0;
import static com.oficina.execution.ExecucaoFixture.em;
import static com.oficina.execution.ExecucaoFixture.itens;
import static com.oficina.execution.ExecucaoFixture.veiculo;
import static com.oficina.execution.domain.valueobject.StatusExecucao.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComandosUseCasesTest {

    private static final Instant AGORA = Instant.parse("2026-10-31T09:30:00Z");

    @Mock ExecucaoRepository repository;
    @Mock PublicadorEventos publicador;

    private final Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
    private final UnidadeDeTrabalhoImediata unidade = new UnidadeDeTrabalhoImediata();
    private RespostaComandos respostas;

    @BeforeEach
    void configurar() {
        respostas = new RespostaComandos(publicador);
    }

    private ComandoRecebido comando(UUID osId, String tipo, DadosComando dados) {
        return new ComandoRecebido(UUID.randomUUID(), tipo, UUID.randomUUID(), osId, dados);
    }

    private EventoIntegracao eventoPublicado(ComandoRecebido comando) {
        var captor = ArgumentCaptor.forClass(EventoIntegracao.class);
        verify(publicador).publicar(eq(comando.osId()), eq(comando.sagaId()), eq(comando.mensagemId()), captor.capture());
        return captor.getValue();
    }

    @Nested
    class EnfileirarDiagnostico {

        private EnfileirarDiagnosticoUseCase useCase;

        @BeforeEach
        void criar() {
            useCase = new EnfileirarDiagnosticoUseCase(repository, respostas, unidade, relogio);
        }

        @Test
        void criaExecucaoERespondeExecucaoEnfileirada() {
            var osId = UUID.randomUUID();
            var clienteId = UUID.randomUUID();
            var comando = comando(osId, "IniciarDiagnostico",
                new DadosComando.IniciarDiagnostico(clienteId, veiculo(), T0));
            when(repository.buscarPorId(osId)).thenReturn(Optional.empty());

            var resultado = useCase.executar(comando, (DadosComando.IniciarDiagnostico) comando.dados());

            assertThat(resultado).isEqualTo(ResultadoProcessamento.APLICADA);
            var captor = ArgumentCaptor.forClass(Execucao.class);
            verify(repository).salvar(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(AGUARDANDO_DIAGNOSTICO);
            assertThat(captor.getValue().getSagaId()).isEqualTo(comando.sagaId());
            assertThat(captor.getValue().getClienteId()).isEqualTo(clienteId);
            assertThat(eventoPublicado(comando)).isEqualTo(new ExecucaoEnfileirada(TipoEtapa.DIAGNOSTICO, AGORA));
            assertThat(unidade.execucoes).isEqualTo(1);
        }

        @Test
        void execucaoExistenteRepeteRespostaComInstanteOriginal() {
            var existente = em(EM_REPARO);
            var comando = comando(existente.getOsId(), "IniciarDiagnostico",
                new DadosComando.IniciarDiagnostico(existente.getClienteId(), veiculo(), T0));
            when(repository.buscarPorId(existente.getOsId())).thenReturn(Optional.of(existente));

            var resultado = useCase.executar(comando, (DadosComando.IniciarDiagnostico) comando.dados());

            assertThat(resultado).isEqualTo(ResultadoProcessamento.IGNORADA);
            verify(repository, never()).salvar(any());
            assertThat(eventoPublicado(comando)).isEqualTo(new ExecucaoEnfileirada(TipoEtapa.DIAGNOSTICO, T0));
        }

        @Test
        void execucaoSemHistoricoUsaInstanteDaFila() {
            var existente = Execucao.reconstituir(new DadosExecucao(UUID.randomUUID(), null, UUID.randomUUID(),
                veiculo(), AGUARDANDO_DIAGNOSTICO, TipoEtapa.DIAGNOSTICO, 0, T0, null, null, null, null,
                null, null, null, 0L));
            var comando = comando(existente.getOsId(), "IniciarDiagnostico",
                new DadosComando.IniciarDiagnostico(existente.getClienteId(), veiculo(), T0));
            when(repository.buscarPorId(existente.getOsId())).thenReturn(Optional.of(existente));

            useCase.executar(comando, (DadosComando.IniciarDiagnostico) comando.dados());

            assertThat(eventoPublicado(comando)).isEqualTo(new ExecucaoEnfileirada(TipoEtapa.DIAGNOSTICO, T0));
        }

        @Test
        void dadosIncompletosSaoRejeitados() {
            var osId = UUID.randomUUID();
            var semVeiculo = comando(osId, "IniciarDiagnostico",
                new DadosComando.IniciarDiagnostico(UUID.randomUUID(), null, T0));
            when(repository.buscarPorId(osId)).thenReturn(Optional.empty());

            var resultado = useCase.executar(semVeiculo, (DadosComando.IniciarDiagnostico) semVeiculo.dados());

            assertThat(resultado).isEqualTo(ResultadoProcessamento.REJEITADA);
            assertThat(eventoPublicado(semVeiculo)).isEqualTo(new ComandoRejeitado(
                "IniciarDiagnostico", "IniciarDiagnostico exige clienteId e veiculo.", "DADOS_INVALIDOS"));
            verify(repository, never()).salvar(any());
        }

        @Test
        void clienteAusenteTambemERejeitado() {
            var osId = UUID.randomUUID();
            var semCliente = comando(osId, "IniciarDiagnostico",
                new DadosComando.IniciarDiagnostico(null, veiculo(), T0));
            when(repository.buscarPorId(osId)).thenReturn(Optional.empty());

            assertThat(useCase.executar(semCliente, (DadosComando.IniciarDiagnostico) semCliente.dados()))
                .isEqualTo(ResultadoProcessamento.REJEITADA);
        }
    }

    @Nested
    class ReabrirDiagnostico {

        private ReabrirDiagnosticoUseCase useCase;

        @BeforeEach
        void criar() {
            useCase = new ReabrirDiagnosticoUseCase(repository, respostas, unidade, relogio);
        }

        @Test
        void reabreDiagnosticoConcluidoSemResposta() {
            var execucao = em(DIAGNOSTICO_CONCLUIDO);
            var comando = comando(execucao.getOsId(), "ReabrirDiagnostico",
                new DadosComando.ReabrirDiagnostico("OS sem itens"));
            when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));

            var resultado = useCase.executar(comando, (DadosComando.ReabrirDiagnostico) comando.dados());

            assertThat(resultado).isEqualTo(ResultadoProcessamento.APLICADA);
            assertThat(execucao.getStatus()).isEqualTo(EM_DIAGNOSTICO);
            assertThat(execucao.getApontamentos()).singleElement()
                .satisfies(a -> assertThat(a.texto()).contains("OS sem itens"));
            verify(repository).salvar(execucao);
            verify(publicador, never()).publicar(any(), any(), any(), any());
        }

        @Test
        void diagnosticoJaReabertoEIgnorado() {
            var execucao = em(EM_DIAGNOSTICO);
            var comando = comando(execucao.getOsId(), "ReabrirDiagnostico", new DadosComando.ReabrirDiagnostico("x"));
            when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));

            assertThat(useCase.executar(comando, (DadosComando.ReabrirDiagnostico) comando.dados()))
                .isEqualTo(ResultadoProcessamento.IGNORADA);
            verify(repository, never()).salvar(any());
            verify(publicador, never()).publicar(any(), any(), any(), any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = {"DIAGNOSTICO_CONCLUIDO", "EM_DIAGNOSTICO"}, mode = EnumSource.Mode.EXCLUDE)
        void outrosStatusRejeitam(StatusExecucao status) {
            var execucao = em(status);
            var comando = comando(execucao.getOsId(), "ReabrirDiagnostico", new DadosComando.ReabrirDiagnostico("x"));
            when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));

            assertThat(useCase.executar(comando, (DadosComando.ReabrirDiagnostico) comando.dados()))
                .isEqualTo(ResultadoProcessamento.REJEITADA);
            assertThat(eventoPublicado(comando)).isInstanceOfSatisfying(ComandoRejeitado.class,
                r -> assertThat(r.codigo()).isEqualTo("ESTADO_INVALIDO"));
        }

        @Test
        void execucaoInexistenteRejeita() {
            var osId = UUID.randomUUID();
            var comando = comando(osId, "ReabrirDiagnostico", new DadosComando.ReabrirDiagnostico("x"));
            when(repository.buscarPorId(osId)).thenReturn(Optional.empty());

            assertThat(useCase.executar(comando, (DadosComando.ReabrirDiagnostico) comando.dados()))
                .isEqualTo(ResultadoProcessamento.REJEITADA);
            assertThat(eventoPublicado(comando)).isInstanceOfSatisfying(ComandoRejeitado.class,
                r -> assertThat(r.codigo()).isEqualTo("EXECUCAO_INEXISTENTE"));
        }
    }

    @Nested
    class EnfileirarReparo {

        private EnfileirarReparoUseCase useCase;

        @BeforeEach
        void criar() {
            useCase = new EnfileirarReparoUseCase(repository, respostas, unidade, relogio);
        }

        @Test
        void colocaNaFilaDeReparoERespondeExecucaoEnfileirada() {
            var execucao = em(DIAGNOSTICO_CONCLUIDO);
            var comando = comando(execucao.getOsId(), "IniciarReparo", new DadosComando.IniciarReparo(itens()));
            when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));

            var resultado = useCase.executar(comando, (DadosComando.IniciarReparo) comando.dados());

            assertThat(resultado).isEqualTo(ResultadoProcessamento.APLICADA);
            assertThat(execucao.getStatus()).isEqualTo(AGUARDANDO_REPARO);
            assertThat(execucao.getItensReparo()).containsExactlyElementsOf(itens());
            verify(repository).salvar(execucao);
            assertThat(eventoPublicado(comando)).isEqualTo(new ExecucaoEnfileirada(TipoEtapa.REPARO, AGORA));
        }

        @Test
        void semItensRejeita() {
            var execucao = em(DIAGNOSTICO_CONCLUIDO);
            var comando = comando(execucao.getOsId(), "IniciarReparo", new DadosComando.IniciarReparo(List.of()));
            when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));

            assertThat(useCase.executar(comando, (DadosComando.IniciarReparo) comando.dados()))
                .isEqualTo(ResultadoProcessamento.REJEITADA);
            assertThat(eventoPublicado(comando)).isInstanceOfSatisfying(ComandoRejeitado.class,
                r -> assertThat(r.codigo()).isEqualTo("DADOS_INVALIDOS"));
        }

        @Test
        void itensNulosRejeitam() {
            var execucao = em(DIAGNOSTICO_CONCLUIDO);
            var comando = comando(execucao.getOsId(), "IniciarReparo", new DadosComando.IniciarReparo(null));
            when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));

            assertThat(useCase.executar(comando, (DadosComando.IniciarReparo) comando.dados()))
                .isEqualTo(ResultadoProcessamento.REJEITADA);
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = {"AGUARDANDO_REPARO", "EM_REPARO", "FINALIZADA", "FALHOU"})
        void reparoJaEnfileiradoRepeteResposta(StatusExecucao status) {
            var execucao = em(status);
            var enfileiradaEm = execucao.enfileiramentoDa(TipoEtapa.REPARO).orElseThrow();
            var comando = comando(execucao.getOsId(), "IniciarReparo", new DadosComando.IniciarReparo(itens()));
            when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));

            assertThat(useCase.executar(comando, (DadosComando.IniciarReparo) comando.dados()))
                .isEqualTo(ResultadoProcessamento.IGNORADA);
            assertThat(eventoPublicado(comando)).isEqualTo(new ExecucaoEnfileirada(TipoEtapa.REPARO, enfileiradaEm));
            verify(repository, never()).salvar(any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = {"AGUARDANDO_DIAGNOSTICO", "EM_DIAGNOSTICO", "CANCELADA"})
        void reparoAntesDoDiagnosticoOuCanceladoRejeita(StatusExecucao status) {
            var execucao = em(status);
            var comando = comando(execucao.getOsId(), "IniciarReparo", new DadosComando.IniciarReparo(itens()));
            when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));

            assertThat(useCase.executar(comando, (DadosComando.IniciarReparo) comando.dados()))
                .isEqualTo(ResultadoProcessamento.REJEITADA);
            assertThat(eventoPublicado(comando)).isInstanceOfSatisfying(ComandoRejeitado.class,
                r -> assertThat(r.codigo()).isEqualTo("ESTADO_INVALIDO"));
        }

        @Test
        void reparoCanceladoAposEnfileiramentoRejeita() {
            var execucao = em(AGUARDANDO_REPARO);
            execucao.cancelar("Pagamento estornado", AGORA);
            var comando = comando(execucao.getOsId(), "IniciarReparo", new DadosComando.IniciarReparo(itens()));
            when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));

            assertThat(useCase.executar(comando, (DadosComando.IniciarReparo) comando.dados()))
                .isEqualTo(ResultadoProcessamento.REJEITADA);
        }

        @Test
        void execucaoInexistenteRejeita() {
            var osId = UUID.randomUUID();
            var comando = comando(osId, "IniciarReparo", new DadosComando.IniciarReparo(itens()));
            when(repository.buscarPorId(osId)).thenReturn(Optional.empty());

            assertThat(useCase.executar(comando, (DadosComando.IniciarReparo) comando.dados()))
                .isEqualTo(ResultadoProcessamento.REJEITADA);
        }
    }

    @Nested
    class CancelarExecucao {

        private CancelarExecucaoUseCase useCase;

        @BeforeEach
        void criar() {
            useCase = new CancelarExecucaoUseCase(repository, respostas, unidade, relogio);
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = {"FINALIZADA", "FALHOU", "CANCELADA"}, mode = EnumSource.Mode.EXCLUDE)
        void cancelaStatusNaoTerminalERespondeExecucaoCancelada(StatusExecucao status) {
            var execucao = em(status);
            var comando = comando(execucao.getOsId(), "CancelarExecucao",
                new DadosComando.CancelarExecucao("Orçamento reprovado"));
            when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));

            assertThat(useCase.executar(comando, (DadosComando.CancelarExecucao) comando.dados()))
                .isEqualTo(ResultadoProcessamento.APLICADA);
            assertThat(execucao.getStatus()).isEqualTo(CANCELADA);
            verify(repository).salvar(execucao);
            assertThat(eventoPublicado(comando)).isEqualTo(new ExecucaoCancelada("Orçamento reprovado", AGORA));
        }

        @Test
        void execucaoJaCanceladaRepeteResposta() {
            var execucao = em(CANCELADA);
            var comando = comando(execucao.getOsId(), "CancelarExecucao", new DadosComando.CancelarExecucao("outro"));
            when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));

            assertThat(useCase.executar(comando, (DadosComando.CancelarExecucao) comando.dados()))
                .isEqualTo(ResultadoProcessamento.IGNORADA);
            assertThat(eventoPublicado(comando))
                .isEqualTo(new ExecucaoCancelada("Orçamento reprovado", T0.plusSeconds(120)));
            verify(repository, never()).salvar(any());
        }

        @Test
        void canceladaSemHistoricoUsaInstanteAtual() {
            var execucao = Execucao.reconstituir(new DadosExecucao(UUID.randomUUID(), null, UUID.randomUUID(),
                veiculo(), CANCELADA, TipoEtapa.DIAGNOSTICO, 0, T0, null, null, null, null,
                null, null, "motivo antigo", 2L));
            var comando = comando(execucao.getOsId(), "CancelarExecucao", new DadosComando.CancelarExecucao("x"));
            when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));

            useCase.executar(comando, (DadosComando.CancelarExecucao) comando.dados());

            assertThat(eventoPublicado(comando)).isEqualTo(new ExecucaoCancelada("motivo antigo", AGORA));
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = {"FINALIZADA", "FALHOU"})
        void execucaoEncerradaRejeita(StatusExecucao status) {
            var execucao = em(status);
            var comando = comando(execucao.getOsId(), "CancelarExecucao", new DadosComando.CancelarExecucao("x"));
            when(repository.buscarPorId(execucao.getOsId())).thenReturn(Optional.of(execucao));

            assertThat(useCase.executar(comando, (DadosComando.CancelarExecucao) comando.dados()))
                .isEqualTo(ResultadoProcessamento.REJEITADA);
            assertThat(eventoPublicado(comando)).isInstanceOfSatisfying(ComandoRejeitado.class, r -> {
                assertThat(r.codigo()).isEqualTo("ESTADO_INVALIDO");
                assertThat(r.comando()).isEqualTo("CancelarExecucao");
            });
            assertThat(execucao.getStatus()).isEqualTo(status);
        }

        @Test
        void execucaoInexistenteRejeita() {
            var osId = UUID.randomUUID();
            var comando = comando(osId, "CancelarExecucao", new DadosComando.CancelarExecucao("x"));
            when(repository.buscarPorId(osId)).thenReturn(Optional.empty());

            assertThat(useCase.executar(comando, (DadosComando.CancelarExecucao) comando.dados()))
                .isEqualTo(ResultadoProcessamento.REJEITADA);
            assertThat(eventoPublicado(comando)).isInstanceOfSatisfying(ComandoRejeitado.class,
                r -> assertThat(r.codigo()).isEqualTo("EXECUCAO_INEXISTENTE"));
        }
    }
}
