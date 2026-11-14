package com.oficina.execution.application.usecase.execucao;

import com.oficina.execution.application.port.UnidadeDeTrabalho;
import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.exception.RecursoNaoEncontradoException;
import com.oficina.execution.domain.repository.ExecucaoRepository;

import java.util.UUID;

public class AlterarPrioridadeUseCase {

    private final ExecucaoRepository repository;
    private final UnidadeDeTrabalho unidadeDeTrabalho;

    public AlterarPrioridadeUseCase(ExecucaoRepository repository, UnidadeDeTrabalho unidadeDeTrabalho) {
        this.repository = repository;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
    }

    public Execucao executar(UUID osId, int prioridade) {
        return unidadeDeTrabalho.executar(() -> {
            var execucao = repository.buscarPorId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Execução", osId));
            execucao.alterarPrioridade(prioridade);
            return repository.salvar(execucao);
        });
    }
}
