package com.oficina.execution.application.usecase.comando;

import com.oficina.execution.application.comando.CodigoRejeicao;
import com.oficina.execution.application.comando.ComandoRecebido;
import com.oficina.execution.application.comando.DadosComando;
import com.oficina.execution.application.comando.RespostaComandos;
import com.oficina.execution.application.comando.ResultadoProcessamento;
import com.oficina.execution.application.port.RegistroMensagensProcessadas;
import com.oficina.execution.application.port.UnidadeDeTrabalho;

import java.time.Clock;

public class ProcessarComandoUseCase {

    private final RegistroMensagensProcessadas mensagensProcessadas;
    private final EnfileirarDiagnosticoUseCase enfileirarDiagnostico;
    private final ReabrirDiagnosticoUseCase reabrirDiagnostico;
    private final EnfileirarReparoUseCase enfileirarReparo;
    private final CancelarExecucaoUseCase cancelarExecucao;
    private final RespostaComandos respostas;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public ProcessarComandoUseCase(RegistroMensagensProcessadas mensagensProcessadas,
                                   EnfileirarDiagnosticoUseCase enfileirarDiagnostico,
                                   ReabrirDiagnosticoUseCase reabrirDiagnostico,
                                   EnfileirarReparoUseCase enfileirarReparo,
                                   CancelarExecucaoUseCase cancelarExecucao,
                                   RespostaComandos respostas,
                                   UnidadeDeTrabalho unidadeDeTrabalho,
                                   Clock relogio) {
        this.mensagensProcessadas = mensagensProcessadas;
        this.enfileirarDiagnostico = enfileirarDiagnostico;
        this.reabrirDiagnostico = reabrirDiagnostico;
        this.enfileirarReparo = enfileirarReparo;
        this.cancelarExecucao = cancelarExecucao;
        this.respostas = respostas;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public ResultadoProcessamento executar(ComandoRecebido comando) {
        return unidadeDeTrabalho.executar(() -> {
            if (mensagensProcessadas.jaProcessada(comando.mensagemId())) {
                return ResultadoProcessamento.DUPLICADA;
            }
            mensagensProcessadas.registrar(comando.mensagemId(), comando.tipo(), relogio.instant());
            return switch (comando.dados()) {
                case DadosComando.IniciarDiagnostico dados -> enfileirarDiagnostico.executar(comando, dados);
                case DadosComando.ReabrirDiagnostico dados -> reabrirDiagnostico.executar(comando, dados);
                case DadosComando.IniciarReparo dados -> enfileirarReparo.executar(comando, dados);
                case DadosComando.CancelarExecucao dados -> cancelarExecucao.executar(comando, dados);
                case DadosComando.NaoReconhecido naoReconhecido -> respostas.rejeitar(comando,
                    CodigoRejeicao.COMANDO_DESCONHECIDO, "Comando não reconhecido: " + comando.tipo() + ".");
                case DadosComando.Invalido invalido -> respostas.rejeitar(comando,
                    CodigoRejeicao.DADOS_INVALIDOS, invalido.erro());
            };
        });
    }
}
