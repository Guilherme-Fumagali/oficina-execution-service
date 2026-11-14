package com.oficina.execution.infrastructure.web.dto.response;

import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.time.Instant;
import java.util.UUID;

public record ExecucaoResumoResponse(
    UUID osId,
    UUID clienteId,
    VeiculoResponse veiculo,
    StatusExecucao status,
    TipoEtapa etapaAtual,
    int prioridade,
    Instant enfileiradaEm
) {

    public static ExecucaoResumoResponse from(Execucao execucao) {
        return new ExecucaoResumoResponse(
            execucao.getOsId(),
            execucao.getClienteId(),
            VeiculoResponse.from(execucao.getVeiculo()),
            execucao.getStatus(),
            execucao.getEtapaAtual(),
            execucao.getPrioridade(),
            execucao.getEnfileiradaEm());
    }
}
