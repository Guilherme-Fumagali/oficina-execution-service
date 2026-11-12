package com.oficina.execution.infrastructure.web.dto.response;

import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExecucaoResponse(
    UUID osId,
    UUID sagaId,
    UUID clienteId,
    VeiculoResponse veiculo,
    StatusExecucao status,
    TipoEtapa etapaAtual,
    int prioridade,
    Instant enfileiradaEm,
    List<ItemReparoResponse> itensReparo,
    List<EtapaResponse> etapas,
    List<ApontamentoResponse> apontamentos,
    List<HistoricoStatusResponse> historicoStatus,
    String observacoesDiagnostico,
    String motivoFalha,
    String motivoCancelamento,
    Long versao
) {

    public static ExecucaoResponse from(Execucao execucao) {
        return new ExecucaoResponse(
            execucao.getOsId(),
            execucao.getSagaId(),
            execucao.getClienteId(),
            VeiculoResponse.from(execucao.getVeiculo()),
            execucao.getStatus(),
            execucao.getEtapaAtual(),
            execucao.getPrioridade(),
            execucao.getEnfileiradaEm(),
            execucao.getItensReparo().stream().map(ItemReparoResponse::from).toList(),
            execucao.getEtapas().stream().map(EtapaResponse::from).toList(),
            execucao.getApontamentos().stream().map(ApontamentoResponse::from).toList(),
            execucao.getHistoricoStatus().stream().map(HistoricoStatusResponse::from).toList(),
            execucao.getObservacoesDiagnostico(),
            execucao.getMotivoFalha(),
            execucao.getMotivoCancelamento(),
            execucao.getVersao());
    }
}
