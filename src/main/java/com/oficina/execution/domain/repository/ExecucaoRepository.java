package com.oficina.execution.domain.repository;

import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecucaoRepository {
    Execucao salvar(Execucao execucao);
    Optional<Execucao> buscarPorId(UUID osId);
    List<Execucao> listarFila(StatusExecucao status, TipoEtapa etapa);
    long contarPorStatus(StatusExecucao status);
}
