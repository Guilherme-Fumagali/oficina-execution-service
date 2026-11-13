package com.oficina.execution.application.usecase.execucao;

import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.exception.RecursoNaoEncontradoException;
import com.oficina.execution.domain.repository.ExecucaoRepository;

import java.util.UUID;

public class ConsultarExecucaoUseCase {

    private final ExecucaoRepository repository;

    public ConsultarExecucaoUseCase(ExecucaoRepository repository) {
        this.repository = repository;
    }

    public Execucao executar(UUID osId) {
        return repository.buscarPorId(osId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Execução", osId));
    }

    public Execucao executarDoCliente(UUID osId, UUID clienteId) {
        var execucao = executar(osId);
        if (!execucao.getClienteId().equals(clienteId)) {
            throw new RecursoNaoEncontradoException("Execução", osId);
        }
        return execucao;
    }
}
