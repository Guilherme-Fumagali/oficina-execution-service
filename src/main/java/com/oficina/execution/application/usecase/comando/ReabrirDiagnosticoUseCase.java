package com.oficina.execution.application.usecase.comando;

import com.oficina.execution.application.comando.CodigoRejeicao;
import com.oficina.execution.application.comando.ComandoRecebido;
import com.oficina.execution.application.comando.DadosComando;
import com.oficina.execution.application.comando.RespostaComandos;
import com.oficina.execution.application.comando.ResultadoProcessamento;
import com.oficina.execution.application.port.UnidadeDeTrabalho;
import com.oficina.execution.domain.repository.ExecucaoRepository;
import com.oficina.execution.domain.valueobject.StatusExecucao;

import java.time.Clock;

public class ReabrirDiagnosticoUseCase {

    private final ExecucaoRepository repository;
    private final RespostaComandos respostas;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public ReabrirDiagnosticoUseCase(ExecucaoRepository repository, RespostaComandos respostas,
                                     UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this.repository = repository;
        this.respostas = respostas;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public ResultadoProcessamento executar(ComandoRecebido comando, DadosComando.ReabrirDiagnostico dados) {
        return unidadeDeTrabalho.executar(() -> {
            var existente = repository.buscarPorId(comando.osId());
            if (existente.isEmpty()) {
                return respostas.rejeitar(comando, CodigoRejeicao.EXECUCAO_INEXISTENTE,
                    "Execução não encontrada para a OS " + comando.osId() + ".");
            }
            var execucao = existente.get();
            if (execucao.getStatus() == StatusExecucao.EM_DIAGNOSTICO) {
                return ResultadoProcessamento.IGNORADA;
            }
            if (execucao.getStatus() != StatusExecucao.DIAGNOSTICO_CONCLUIDO) {
                return respostas.rejeitar(comando, CodigoRejeicao.ESTADO_INVALIDO,
                    "Diagnóstico não pode ser reaberto no status " + execucao.getStatus() + ".");
            }
            execucao.reabrirDiagnostico(dados.motivo(), relogio.instant());
            repository.salvar(execucao);
            return ResultadoProcessamento.APLICADA;
        });
    }
}
