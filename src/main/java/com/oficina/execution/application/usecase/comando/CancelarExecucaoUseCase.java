package com.oficina.execution.application.usecase.comando;

import com.oficina.execution.application.comando.CodigoRejeicao;
import com.oficina.execution.application.comando.ComandoRecebido;
import com.oficina.execution.application.comando.DadosComando;
import com.oficina.execution.application.comando.RespostaComandos;
import com.oficina.execution.application.comando.ResultadoProcessamento;
import com.oficina.execution.application.evento.ExecucaoCancelada;
import com.oficina.execution.application.port.UnidadeDeTrabalho;
import com.oficina.execution.domain.repository.ExecucaoRepository;
import com.oficina.execution.domain.valueobject.StatusExecucao;

import java.time.Clock;

public class CancelarExecucaoUseCase {

    private final ExecucaoRepository repository;
    private final RespostaComandos respostas;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public CancelarExecucaoUseCase(ExecucaoRepository repository, RespostaComandos respostas,
                                   UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this.repository = repository;
        this.respostas = respostas;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public ResultadoProcessamento executar(ComandoRecebido comando, DadosComando.CancelarExecucao dados) {
        return unidadeDeTrabalho.executar(() -> {
            var existente = repository.buscarPorId(comando.osId());
            if (existente.isEmpty()) {
                return respostas.rejeitar(comando, CodigoRejeicao.EXECUCAO_INEXISTENTE,
                    "Execução não encontrada para a OS " + comando.osId() + ".");
            }
            var execucao = existente.get();
            var agora = relogio.instant();
            if (execucao.getStatus() == StatusExecucao.CANCELADA) {
                var canceladaEm = execucao.instanteDoStatus(StatusExecucao.CANCELADA).orElse(agora);
                respostas.responder(comando, new ExecucaoCancelada(execucao.getMotivoCancelamento(), canceladaEm));
                return ResultadoProcessamento.IGNORADA;
            }
            if (execucao.isTerminal()) {
                return respostas.rejeitar(comando, CodigoRejeicao.ESTADO_INVALIDO,
                    "Execução encerrada com status " + execucao.getStatus() + " não pode ser cancelada.");
            }
            execucao.cancelar(dados.motivo(), agora);
            repository.salvar(execucao);
            respostas.responder(comando, new ExecucaoCancelada(dados.motivo(), agora));
            return ResultadoProcessamento.APLICADA;
        });
    }
}
