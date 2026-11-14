package com.oficina.execution.application.usecase.execucao;

import com.oficina.execution.application.port.UnidadeDeTrabalho;
import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.exception.RecursoNaoEncontradoException;
import com.oficina.execution.domain.repository.ExecucaoRepository;

import java.time.Clock;
import java.util.UUID;

public class RegistrarApontamentoUseCase {

    private final ExecucaoRepository repository;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public RegistrarApontamentoUseCase(ExecucaoRepository repository, UnidadeDeTrabalho unidadeDeTrabalho,
            Clock relogio) {
        this.repository = repository;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public Execucao executar(UUID osId, UUID mecanicoId, String texto) {
        return unidadeDeTrabalho.executar(() -> {
            var execucao = repository.buscarPorId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Execução", osId));
            execucao.registrarApontamento(mecanicoId, texto, relogio.instant());
            return repository.salvar(execucao);
        });
    }
}
