package com.oficina.execution.application.usecase.comando;

import com.oficina.execution.application.comando.CodigoRejeicao;
import com.oficina.execution.application.comando.ComandoRecebido;
import com.oficina.execution.application.comando.DadosComando;
import com.oficina.execution.application.comando.RespostaComandos;
import com.oficina.execution.application.comando.ResultadoProcessamento;
import com.oficina.execution.application.evento.ExecucaoEnfileirada;
import com.oficina.execution.application.port.UnidadeDeTrabalho;
import com.oficina.execution.domain.repository.ExecucaoRepository;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.time.Clock;

public class EnfileirarReparoUseCase {

    private final ExecucaoRepository repository;
    private final RespostaComandos respostas;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public EnfileirarReparoUseCase(ExecucaoRepository repository, RespostaComandos respostas,
                                   UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this.repository = repository;
        this.respostas = respostas;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public ResultadoProcessamento executar(ComandoRecebido comando, DadosComando.IniciarReparo dados) {
        return unidadeDeTrabalho.executar(() -> {
            var existente = repository.buscarPorId(comando.osId());
            if (existente.isEmpty()) {
                return respostas.rejeitar(comando, CodigoRejeicao.EXECUCAO_INEXISTENTE,
                    "Execução não encontrada para a OS " + comando.osId() + ".");
            }
            var execucao = existente.get();
            if (execucao.getStatus() == StatusExecucao.DIAGNOSTICO_CONCLUIDO) {
                if (dados.itens() == null || dados.itens().isEmpty()) {
                    return respostas.rejeitar(comando, CodigoRejeicao.DADOS_INVALIDOS,
                        "IniciarReparo exige ao menos um item.");
                }
                execucao.enfileirarReparo(dados.itens(), relogio.instant());
                repository.salvar(execucao);
                respostas.responder(comando, new ExecucaoEnfileirada(TipoEtapa.REPARO, execucao.getEnfileiradaEm()));
                return ResultadoProcessamento.APLICADA;
            }
            var enfileiramento = execucao.enfileiramentoDa(TipoEtapa.REPARO);
            if (execucao.getStatus() != StatusExecucao.CANCELADA && enfileiramento.isPresent()) {
                respostas.responder(comando, new ExecucaoEnfileirada(TipoEtapa.REPARO, enfileiramento.get()));
                return ResultadoProcessamento.IGNORADA;
            }
            return respostas.rejeitar(comando, CodigoRejeicao.ESTADO_INVALIDO,
                "Reparo não pode ser enfileirado no status " + execucao.getStatus() + ".");
        });
    }
}
