package com.oficina.execution.infrastructure.web.dto.response;

import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExecucaoClienteResponse(
    UUID osId,
    UUID clienteId,
    VeiculoResponse veiculo,
    StatusExecucao status,
    TipoEtapa etapaAtual,
    Instant enfileiradaEm,
    List<ItemReparoResponse> itensReparo,
    List<EtapaClienteResponse> etapas,
    List<HistoricoStatusResponse> historicoStatus,
    String observacoesDiagnostico,
    String motivoFalha,
    String motivoCancelamento
) {

    public static ExecucaoClienteResponse from(Execucao execucao) {
        return new ExecucaoClienteResponse(
            execucao.getOsId(),
            execucao.getClienteId(),
            VeiculoResponse.from(execucao.getVeiculo()),
            execucao.getStatus(),
            execucao.getEtapaAtual(),
            execucao.getEnfileiradaEm(),
            execucao.getItensReparo().stream().map(ItemReparoResponse::from).toList(),
            execucao.getEtapas().stream().map(EtapaClienteResponse::from).toList(),
            execucao.getHistoricoStatus().stream().map(HistoricoStatusResponse::from).toList(),
            execucao.getObservacoesDiagnostico(),
            execucao.getMotivoFalha(),
            execucao.getMotivoCancelamento());
    }
}
