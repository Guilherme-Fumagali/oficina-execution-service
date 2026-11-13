package com.oficina.execution.domain;

import com.oficina.execution.domain.entity.Apontamento;
import com.oficina.execution.domain.entity.DadosExecucao;
import com.oficina.execution.domain.entity.Etapa;
import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.entity.HistoricoStatus;
import com.oficina.execution.domain.entity.ItemReparo;
import com.oficina.execution.domain.entity.Veiculo;
import com.oficina.execution.domain.exception.DomainException;
import com.oficina.execution.domain.exception.OperacaoNaoPermitidaException;
import com.oficina.execution.domain.exception.TransicaoInvalidaException;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;
import com.oficina.execution.domain.valueobject.TipoItemReparo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.oficina.execution.ExecucaoFixture.MECANICO;
import static com.oficina.execution.ExecucaoFixture.T0;
import static com.oficina.execution.ExecucaoFixture.em;
import static com.oficina.execution.ExecucaoFixture.itens;
import static com.oficina.execution.ExecucaoFixture.nova;
import static com.oficina.execution.ExecucaoFixture.veiculo;
import static com.oficina.execution.domain.valueobject.StatusExecucao.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecucaoTest {

    private static final Instant T1 = T0.plusSeconds(900);

    @Nested
    class Enfileiramento {

        @Test
        void comandoIniciarDiagnosticoCriaExecucaoNaFila() {
            var osId = UUID.randomUUID();
            var sagaId = UUID.randomUUID();
            var clienteId = UUID.randomUUID();

            var execucao = Execucao.enfileirarDiagnostico(osId, sagaId, clienteId, veiculo(), T0);

            assertThat(execucao.getOsId()).isEqualTo(osId);
            assertThat(execucao.getSagaId()).isEqualTo(sagaId);
            assertThat(execucao.getClienteId()).isEqualTo(clienteId);
            assertThat(execucao.getVeiculo()).isEqualTo(veiculo());
            assertThat(execucao.getStatus()).isEqualTo(AGUARDANDO_DIAGNOSTICO);
            assertThat(execucao.getEtapaAtual()).isEqualTo(TipoEtapa.DIAGNOSTICO);
            assertThat(execucao.getPrioridade()).isZero();
            assertThat(execucao.getEnfileiradaEm()).isEqualTo(T0);
            assertThat(execucao.getVersao()).isNull();
            assertThat(execucao.getEtapas()).isEmpty();
            assertThat(execucao.getItensReparo()).isEmpty();
            assertThat(execucao.getHistoricoStatus())
                .containsExactly(new HistoricoStatus(null, AGUARDANDO_DIAGNOSTICO, T0, null));
        }

        @Test
        void exigeOrdemDeServicoClienteEVeiculo() {
            var id = UUID.randomUUID();
            var veiculo = veiculo();
            assertThatThrownBy(() -> Execucao.enfileirarDiagnostico(null, id, id, veiculo, T0))
                .isInstanceOf(DomainException.class).hasMessageContaining("ordem de serviço");
            assertThatThrownBy(() -> Execucao.enfileirarDiagnostico(id, id, null, veiculo, T0))
                .isInstanceOf(DomainException.class).hasMessageContaining("cliente");
            assertThatThrownBy(() -> Execucao.enfileirarDiagnostico(id, id, id, null, T0))
                .isInstanceOf(DomainException.class).hasMessageContaining("veículo");
        }
    }

    @Nested
    class Diagnostico {

        @Test
        void mecanicoIniciaDiagnosticoAbrindoEtapa() {
            var execucao = em(AGUARDANDO_DIAGNOSTICO);

            execucao.iniciarDiagnostico(MECANICO, T1);

            assertThat(execucao.getStatus()).isEqualTo(EM_DIAGNOSTICO);
            assertThat(execucao.getEtapas()).containsExactly(new Etapa(TipoEtapa.DIAGNOSTICO, MECANICO, T1, null));
            assertThat(execucao.getHistoricoStatus()).last()
                .isEqualTo(new HistoricoStatus(AGUARDANDO_DIAGNOSTICO, EM_DIAGNOSTICO, T1, null));
        }

        @Test
        void inicioDeDiagnosticoExigeMecanico() {
            var execucao = em(AGUARDANDO_DIAGNOSTICO);
            assertThatThrownBy(() -> execucao.iniciarDiagnostico(null, T1))
                .isInstanceOf(DomainException.class).hasMessageContaining("mecânico");
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = "AGUARDANDO_DIAGNOSTICO", mode = EnumSource.Mode.EXCLUDE)
        void inicioDeDiagnosticoSoNaFila(StatusExecucao status) {
            var execucao = em(status);
            assertThatThrownBy(() -> execucao.iniciarDiagnostico(MECANICO, T1))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Transição inválida: %s → EM_DIAGNOSTICO".formatted(status));
            assertThat(execucao.getStatus()).isEqualTo(status);
        }

        @Test
        void conclusaoEncerraEtapaERegistraHistorico() {
            var execucao = em(EM_DIAGNOSTICO);

            execucao.concluirDiagnostico("Pastilhas gastas", T1);

            assertThat(execucao.getStatus()).isEqualTo(DIAGNOSTICO_CONCLUIDO);
            assertThat(execucao.getObservacoesDiagnostico()).isEqualTo("Pastilhas gastas");
            assertThat(execucao.getEtapas()).singleElement()
                .satisfies(e -> assertThat(e.fim()).isEqualTo(T1));
            assertThat(execucao.getHistoricoStatus()).last()
                .isEqualTo(new HistoricoStatus(EM_DIAGNOSTICO, DIAGNOSTICO_CONCLUIDO, T1, null));
        }

        @Test
        void conclusaoExigeObservacoes() {
            var execucao = em(EM_DIAGNOSTICO);
            assertThatThrownBy(() -> execucao.concluirDiagnostico("  ", T1))
                .isInstanceOf(DomainException.class).hasMessageContaining("observações");
            assertThat(execucao.getStatus()).isEqualTo(EM_DIAGNOSTICO);
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = "EM_DIAGNOSTICO", mode = EnumSource.Mode.EXCLUDE)
        void conclusaoSoDuranteDiagnostico(StatusExecucao status) {
            var execucao = em(status);
            assertThatThrownBy(() -> execucao.concluirDiagnostico("Observação", T1))
                .isInstanceOf(TransicaoInvalidaException.class);
        }

        @Test
        void reaberturaVoltaAoDiagnosticoComApontamentoAutomatico() {
            var execucao = em(DIAGNOSTICO_CONCLUIDO);

            execucao.reabrirDiagnostico("Diagnóstico sem itens", T1);

            assertThat(execucao.getStatus()).isEqualTo(EM_DIAGNOSTICO);
            assertThat(execucao.getEtapas()).hasSize(2).last()
                .isEqualTo(new Etapa(TipoEtapa.DIAGNOSTICO, MECANICO, T1, null));
            assertThat(execucao.getApontamentos())
                .containsExactly(new Apontamento(null, "Diagnóstico reaberto: Diagnóstico sem itens", T1));
            assertThat(execucao.getHistoricoStatus()).last()
                .isEqualTo(new HistoricoStatus(DIAGNOSTICO_CONCLUIDO, EM_DIAGNOSTICO, T1, "Diagnóstico sem itens"));
        }

        @Test
        void reaberturaSemMotivoUsaTextoPadrao() {
            var execucao = em(DIAGNOSTICO_CONCLUIDO);

            execucao.reabrirDiagnostico(null, T1);

            assertThat(execucao.getApontamentos()).extracting(Apontamento::texto)
                .containsExactly("Diagnóstico reaberto.");
        }

        @Test
        void reaberturaSemEtapaAnteriorFicaSemMecanico() {
            var execucao = Execucao.reconstituir(new DadosExecucao(UUID.randomUUID(), null, UUID.randomUUID(),
                veiculo(), DIAGNOSTICO_CONCLUIDO, TipoEtapa.DIAGNOSTICO, 0, T0,
                null, null, null, null, null, null, null, 3L));

            execucao.reabrirDiagnostico("Sem itens", T1);

            assertThat(execucao.getEtapas()).singleElement()
                .satisfies(e -> assertThat(e.mecanicoId()).isNull());
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = "DIAGNOSTICO_CONCLUIDO", mode = EnumSource.Mode.EXCLUDE)
        void reaberturaSoAposConclusao(StatusExecucao status) {
            var execucao = em(status);
            assertThatThrownBy(() -> execucao.reabrirDiagnostico("motivo", T1))
                .isInstanceOf(TransicaoInvalidaException.class);
        }
    }

    @Nested
    class Reparo {

        @Test
        void comandoIniciarReparoColocaNaFilaDeReparo() {
            var execucao = em(DIAGNOSTICO_CONCLUIDO);

            execucao.enfileirarReparo(itens(), T1);

            assertThat(execucao.getStatus()).isEqualTo(AGUARDANDO_REPARO);
            assertThat(execucao.getEtapaAtual()).isEqualTo(TipoEtapa.REPARO);
            assertThat(execucao.getEnfileiradaEm()).isEqualTo(T1);
            assertThat(execucao.getItensReparo()).containsExactlyElementsOf(itens());
            assertThat(execucao.enfileiramentoDa(TipoEtapa.REPARO)).contains(T1);
            assertThat(execucao.enfileiramentoDa(TipoEtapa.DIAGNOSTICO)).contains(T0);
        }

        @Test
        void reparoExigeItens() {
            var execucao = em(DIAGNOSTICO_CONCLUIDO);
            assertThatThrownBy(() -> execucao.enfileirarReparo(List.of(), T1))
                .isInstanceOf(DomainException.class).hasMessageContaining("item");
            assertThatThrownBy(() -> execucao.enfileirarReparo(null, T1))
                .isInstanceOf(DomainException.class);
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = "DIAGNOSTICO_CONCLUIDO", mode = EnumSource.Mode.EXCLUDE)
        void reparoSoAposDiagnosticoConcluido(StatusExecucao status) {
            var execucao = em(status);
            var itens = itens();
            assertThatThrownBy(() -> execucao.enfileirarReparo(itens, T1))
                .isInstanceOf(TransicaoInvalidaException.class);
        }

        @Test
        void mecanicoIniciaEConcluiReparo() {
            var execucao = em(AGUARDANDO_REPARO);
            var inicio = T1;
            var fim = T1.plusSeconds(1800);

            execucao.iniciarReparo(MECANICO, inicio);
            execucao.concluirReparo(fim);

            assertThat(execucao.getStatus()).isEqualTo(FINALIZADA);
            assertThat(execucao.isTerminal()).isTrue();
            assertThat(execucao.ultimaEtapa(TipoEtapa.REPARO))
                .contains(new Etapa(TipoEtapa.REPARO, MECANICO, inicio, fim));
            assertThat(execucao.getHistoricoStatus()).last()
                .isEqualTo(new HistoricoStatus(EM_REPARO, FINALIZADA, fim, null));
        }

        @Test
        void inicioDeReparoExigeMecanico() {
            var execucao = em(AGUARDANDO_REPARO);
            assertThatThrownBy(() -> execucao.iniciarReparo(null, T1)).isInstanceOf(DomainException.class);
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = "AGUARDANDO_REPARO", mode = EnumSource.Mode.EXCLUDE)
        void inicioDeReparoSoNaFilaDeReparo(StatusExecucao status) {
            var execucao = em(status);
            assertThatThrownBy(() -> execucao.iniciarReparo(MECANICO, T1))
                .isInstanceOf(TransicaoInvalidaException.class);
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = "EM_REPARO", mode = EnumSource.Mode.EXCLUDE)
        void conclusaoDeReparoSoDuranteReparo(StatusExecucao status) {
            var execucao = em(status);
            assertThatThrownBy(() -> execucao.concluirReparo(T1))
                .isInstanceOf(TransicaoInvalidaException.class);
        }
    }

    @Nested
    class Falha {

        @Test
        void falhaDuranteReparoEncerraEtapa() {
            var execucao = em(EM_REPARO);

            execucao.registrarFalha("Peça incompatível", T1);

            assertThat(execucao.getStatus()).isEqualTo(FALHOU);
            assertThat(execucao.getMotivoFalha()).isEqualTo("Peça incompatível");
            assertThat(execucao.ultimaEtapa(TipoEtapa.REPARO)).hasValueSatisfying(e -> assertThat(e.fim()).isEqualTo(T1));
            assertThat(execucao.getHistoricoStatus()).last()
                .isEqualTo(new HistoricoStatus(EM_REPARO, FALHOU, T1, "Peça incompatível"));
        }

        @Test
        void falhaNaFilaDeReparo() {
            var execucao = em(AGUARDANDO_REPARO);

            execucao.registrarFalha("Veículo retirado", T1);

            assertThat(execucao.getStatus()).isEqualTo(FALHOU);
            assertThat(execucao.ultimaEtapa(TipoEtapa.REPARO)).isEmpty();
        }

        @Test
        void falhaExigeMotivo() {
            var execucao = em(EM_REPARO);
            assertThatThrownBy(() -> execucao.registrarFalha(" ", T1))
                .isInstanceOf(DomainException.class).hasMessageContaining("motivo");
            assertThat(execucao.getStatus()).isEqualTo(EM_REPARO);
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = {"AGUARDANDO_REPARO", "EM_REPARO"}, mode = EnumSource.Mode.EXCLUDE)
        void falhaSoNaEtapaDeReparo(StatusExecucao status) {
            var execucao = em(status);
            assertThatThrownBy(() -> execucao.registrarFalha("motivo", T1))
                .isInstanceOf(TransicaoInvalidaException.class);
        }
    }

    @Nested
    class Cancelamento {

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = {"FINALIZADA", "FALHOU", "CANCELADA"}, mode = EnumSource.Mode.EXCLUDE)
        void qualquerStatusNaoTerminalPodeSerCancelado(StatusExecucao status) {
            var execucao = em(status);

            execucao.cancelar("Orçamento reprovado", T1);

            assertThat(execucao.getStatus()).isEqualTo(CANCELADA);
            assertThat(execucao.getMotivoCancelamento()).isEqualTo("Orçamento reprovado");
            assertThat(execucao.getEtapas()).allSatisfy(e -> assertThat(e.emAndamento()).isFalse());
            assertThat(execucao.instanteDoStatus(CANCELADA)).contains(T1);
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = {"FINALIZADA", "FALHOU", "CANCELADA"})
        void statusTerminalNaoPodeSerCancelado(StatusExecucao status) {
            var execucao = em(status);
            assertThatThrownBy(() -> execucao.cancelar("motivo", T1))
                .isInstanceOf(TransicaoInvalidaException.class);
        }
    }

    @Nested
    class ApontamentosEPrioridade {

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = {"FINALIZADA", "FALHOU", "CANCELADA"}, mode = EnumSource.Mode.EXCLUDE)
        void apontamentoAceitoEmStatusNaoTerminal(StatusExecucao status) {
            var execucao = em(status);

            execucao.registrarApontamento(MECANICO, "Cliente autorizou teste de rodagem", T1);

            assertThat(execucao.getApontamentos()).last()
                .isEqualTo(new Apontamento(MECANICO, "Cliente autorizou teste de rodagem", T1));
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = {"FINALIZADA", "FALHOU", "CANCELADA"})
        void apontamentoRecusadoEmStatusTerminal(StatusExecucao status) {
            var execucao = em(status);
            assertThatThrownBy(() -> execucao.registrarApontamento(MECANICO, "texto", T1))
                .isInstanceOf(OperacaoNaoPermitidaException.class)
                .hasMessage("Operação 'registrar apontamento' não permitida no status %s.".formatted(status));
        }

        @Test
        void apontamentoExigeTextoEMecanico() {
            var execucao = em(EM_DIAGNOSTICO);
            assertThatThrownBy(() -> execucao.registrarApontamento(MECANICO, "", T1))
                .isInstanceOf(DomainException.class).hasMessageContaining("texto");
            assertThatThrownBy(() -> execucao.registrarApontamento(null, "texto", T1))
                .isInstanceOf(DomainException.class).hasMessageContaining("mecânico");
        }

        @Test
        void prioridadeAlteradaEmStatusNaoTerminal() {
            var execucao = em(AGUARDANDO_DIAGNOSTICO);

            execucao.alterarPrioridade(5);

            assertThat(execucao.getPrioridade()).isEqualTo(5);
        }

        @ParameterizedTest
        @EnumSource(value = StatusExecucao.class, names = {"FINALIZADA", "FALHOU", "CANCELADA"})
        void prioridadeNaoMudaEmStatusTerminal(StatusExecucao status) {
            var execucao = em(status);
            assertThatThrownBy(() -> execucao.alterarPrioridade(3))
                .isInstanceOf(OperacaoNaoPermitidaException.class);
        }
    }

    @Nested
    class Reconstituicao {

        @Test
        void reconstituiComListasVaziasEVersao() {
            var osId = UUID.randomUUID();
            var execucao = Execucao.reconstituir(new DadosExecucao(osId, null, UUID.randomUUID(), veiculo(),
                AGUARDANDO_DIAGNOSTICO, TipoEtapa.DIAGNOSTICO, 2, T0, null, null, null, null, null, null, null, 7L));

            assertThat(execucao.getVersao()).isEqualTo(7L);
            assertThat(execucao.getPrioridade()).isEqualTo(2);
            assertThat(execucao.getHistoricoStatus()).isEmpty();
            assertThat(execucao.instanteDoStatus(AGUARDANDO_DIAGNOSTICO)).isEmpty();

            execucao.sincronizarVersao(8L);
            assertThat(execucao.getVersao()).isEqualTo(8L);
        }

        @Test
        void listasExpostasSaoSomenteLeitura() {
            var execucao = em(EM_DIAGNOSTICO);
            var etapas = execucao.getEtapas();
            var apontamentos = execucao.getApontamentos();
            var historico = execucao.getHistoricoStatus();
            var itens = execucao.getItensReparo();
            var etapa = new Etapa(TipoEtapa.DIAGNOSTICO, MECANICO, T0, null);
            var apontamento = new Apontamento(MECANICO, "x", T0);
            var entrada = new HistoricoStatus(null, AGUARDANDO_DIAGNOSTICO, T0, null);
            var item = new ItemReparo(TipoItemReparo.PECA, "Correia", 1);

            assertThatThrownBy(() -> etapas.add(etapa)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> apontamentos.add(apontamento)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> historico.add(entrada)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> itens.add(item)).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class ObjetosDeValor {

        @Test
        void veiculoExigePlaca() {
            assertThatThrownBy(() -> new Veiculo(" ", "Fiat", "Uno", 2010))
                .isInstanceOf(DomainException.class).hasMessageContaining("placa");
            assertThatThrownBy(() -> new Veiculo(null, "Fiat", "Uno", 2010))
                .isInstanceOf(DomainException.class);
        }

        @Test
        void itemDeReparoValidaTipoDescricaoEQuantidade() {
            assertThatThrownBy(() -> new ItemReparo(null, "Óleo", 1))
                .isInstanceOf(DomainException.class).hasMessageContaining("tipo");
            assertThatThrownBy(() -> new ItemReparo(TipoItemReparo.PECA, " ", 1))
                .isInstanceOf(DomainException.class).hasMessageContaining("descrição");
            assertThatThrownBy(() -> new ItemReparo(TipoItemReparo.PECA, null, 1))
                .isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> new ItemReparo(TipoItemReparo.PECA, "Óleo", 0))
                .isInstanceOf(DomainException.class).hasMessageContaining("quantidade");
        }

        @Test
        void execucaoNovaNaoTemVersao() {
            assertThat(nova().getVersao()).isNull();
        }
    }
}
