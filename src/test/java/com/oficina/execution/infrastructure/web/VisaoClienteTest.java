package com.oficina.execution.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.infrastructure.web.dto.response.ExecucaoClienteResponse;
import com.oficina.execution.infrastructure.web.dto.response.ExecucaoResponse;
import com.oficina.execution.infrastructure.web.dto.response.ExecucaoResumoResponse;
import org.junit.jupiter.api.Test;

import static com.oficina.execution.ExecucaoFixture.MECANICO;
import static com.oficina.execution.ExecucaoFixture.T0;
import static com.oficina.execution.ExecucaoFixture.em;
import static org.assertj.core.api.Assertions.assertThat;

class VisaoClienteTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void visaoDoClienteOmiteApontamentosMecanicoEDadosInternos() throws Exception {
        var execucao = em(StatusExecucao.EM_REPARO);
        execucao.registrarApontamento(MECANICO, "Cliente pediu orçamento de pneus", T0.plusSeconds(4100));

        var json = mapper.readTree(mapper.writeValueAsString(ExecucaoClienteResponse.from(execucao)));

        assertThat(json.has("apontamentos")).isFalse();
        assertThat(json.has("sagaId")).isFalse();
        assertThat(json.has("prioridade")).isFalse();
        assertThat(json.has("versao")).isFalse();
        assertThat(json.toString()).doesNotContain("mecanicoId").doesNotContain(MECANICO.toString())
            .doesNotContain("orçamento de pneus");
        assertThat(json.get("etapas")).hasSize(2);
        assertThat(json.get("etapas").get(1).get("tipo").asText()).isEqualTo("REPARO");
        assertThat(json.get("historicoStatus")).hasSize(execucao.getHistoricoStatus().size());
        assertThat(json.get("observacoesDiagnostico").asText()).isEqualTo("Vazamento no cárter");
        assertThat(json.get("veiculo").get("placa").asText()).isEqualTo("ABC1D23");
    }

    @Test
    void visaoDoFuncionarioTrazApontamentosEMecanico() {
        var execucao = em(StatusExecucao.EM_REPARO);
        execucao.registrarApontamento(MECANICO, "Aguardando peça", T0.plusSeconds(4100));

        var resposta = ExecucaoResponse.from(execucao);

        assertThat(resposta.apontamentos()).singleElement()
            .satisfies(a -> assertThat(a.mecanicoId()).isEqualTo(MECANICO));
        assertThat(resposta.etapas()).allSatisfy(e -> assertThat(e.mecanicoId()).isEqualTo(MECANICO));
        assertThat(resposta.sagaId()).isEqualTo(execucao.getSagaId());
    }

    @Test
    void resumoDaFilaTrazPrioridadeEEntrada() {
        var execucao = em(StatusExecucao.AGUARDANDO_REPARO);
        execucao.alterarPrioridade(4);

        var resumo = ExecucaoResumoResponse.from(execucao);

        assertThat(resumo.prioridade()).isEqualTo(4);
        assertThat(resumo.enfileiradaEm()).isEqualTo(execucao.getEnfileiradaEm());
        assertThat(resumo.etapaAtual()).isEqualTo(execucao.getEtapaAtual());
    }
}
