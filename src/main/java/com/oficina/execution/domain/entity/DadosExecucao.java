package com.oficina.execution.domain.entity;

import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DadosExecucao(
    UUID osId,
    UUID sagaId,
    UUID clienteId,
    Veiculo veiculo,
    StatusExecucao status,
    TipoEtapa etapaAtual,
    int prioridade,
    Instant enfileiradaEm,
    List<ItemReparo> itensReparo,
    List<Etapa> etapas,
    List<Apontamento> apontamentos,
    List<HistoricoStatus> historicoStatus,
    String observacoesDiagnostico,
    String motivoFalha,
    String motivoCancelamento,
    Long versao
) {}
