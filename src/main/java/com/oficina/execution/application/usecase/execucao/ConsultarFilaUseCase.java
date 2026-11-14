package com.oficina.execution.application.usecase.execucao;

import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.repository.ExecucaoRepository;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.util.List;

public class ConsultarFilaUseCase {

    private final ExecucaoRepository repository;

    public ConsultarFilaUseCase(ExecucaoRepository repository) {
        this.repository = repository;
    }

    public List<Execucao> executar(StatusExecucao status, TipoEtapa etapa) {
        return repository.listarFila(status, etapa);
    }
}
