package com.oficina.execution.application.usecase.comando;

import com.oficina.execution.application.comando.CodigoRejeicao;
import com.oficina.execution.application.comando.ComandoRecebido;
import com.oficina.execution.application.comando.DadosComando;
import com.oficina.execution.application.comando.RespostaComandos;
import com.oficina.execution.application.comando.ResultadoProcessamento;
import com.oficina.execution.application.evento.ExecucaoEnfileirada;
import com.oficina.execution.application.port.UnidadeDeTrabalho;
import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.repository.ExecucaoRepository;
import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.time.Clock;

public class EnfileirarDiagnosticoUseCase {

    private final ExecucaoRepository repository;
    private final RespostaComandos respostas;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public EnfileirarDiagnosticoUseCase(ExecucaoRepository repository, RespostaComandos respostas,
                                        UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this.repository = repository;
        this.respostas = respostas;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public ResultadoProcessamento executar(ComandoRecebido comando, DadosComando.IniciarDiagnostico dados) {
        return unidadeDeTrabalho.executar(() -> {
            var existente = repository.buscarPorId(comando.osId());
            if (existente.isPresent()) {
                var execucao = existente.get();
                var enfileiradaEm = execucao.enfileiramentoDa(TipoEtapa.DIAGNOSTICO).orElse(execucao.getEnfileiradaEm());
                respostas.responder(comando, new ExecucaoEnfileirada(TipoEtapa.DIAGNOSTICO, enfileiradaEm));
                return ResultadoProcessamento.IGNORADA;
            }
            if (dados.clienteId() == null || dados.veiculo() == null) {
                return respostas.rejeitar(comando, CodigoRejeicao.DADOS_INVALIDOS,
                    "IniciarDiagnostico exige clienteId e veiculo.");
            }
            var execucao = Execucao.enfileirarDiagnostico(
                comando.osId(), comando.sagaId(), dados.clienteId(), dados.veiculo(), relogio.instant());
            repository.salvar(execucao);
            respostas.responder(comando, new ExecucaoEnfileirada(TipoEtapa.DIAGNOSTICO, execucao.getEnfileiradaEm()));
            return ResultadoProcessamento.APLICADA;
        });
    }
}
