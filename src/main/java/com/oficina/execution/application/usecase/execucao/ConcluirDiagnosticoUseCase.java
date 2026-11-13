package com.oficina.execution.application.usecase.execucao;

import com.oficina.execution.application.evento.DiagnosticoConcluido;
import com.oficina.execution.application.port.PublicadorEventos;
import com.oficina.execution.application.port.UnidadeDeTrabalho;
import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.exception.RecursoNaoEncontradoException;
import com.oficina.execution.domain.repository.ExecucaoRepository;

import java.time.Clock;
import java.util.UUID;

public class ConcluirDiagnosticoUseCase {

    private final ExecucaoRepository repository;
    private final PublicadorEventos publicador;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public ConcluirDiagnosticoUseCase(ExecucaoRepository repository, PublicadorEventos publicador,
            UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this.repository = repository;
        this.publicador = publicador;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public Execucao executar(UUID osId, UUID mecanicoId, String observacoes) {
        return unidadeDeTrabalho.executar(() -> {
            var execucao = repository.buscarPorId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Execução", osId));
            var agora = relogio.instant();
            execucao.concluirDiagnostico(observacoes, agora);
            repository.salvar(execucao);
            publicador.publicar(osId, execucao.getSagaId(), null, new DiagnosticoConcluido(mecanicoId, agora, observacoes));
            return execucao;
        });
    }
}
