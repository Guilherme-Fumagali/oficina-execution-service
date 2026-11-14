package com.oficina.execution.infrastructure.messaging;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.TransportType;
import com.oficina.execution.application.comando.ComandoRecebido;
import com.oficina.execution.application.comando.ResultadoProcessamento;
import com.oficina.execution.application.port.RegistroMensagensProcessadas;
import com.oficina.execution.application.usecase.comando.ProcessarComandoUseCase;
import com.oficina.execution.infrastructure.observability.MetricasMensageria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProcessadorComandos {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorComandos.class);

    static final String TIPO_DESCONHECIDO = "desconhecido";
    static final String RESULTADO_ERRO = "erro";

    private final LeitorComandos leitor;
    private final ProcessarComandoUseCase processarComando;
    private final RegistroMensagensProcessadas mensagensProcessadas;
    private final MetricasMensageria metricas;

    public ProcessadorComandos(LeitorComandos leitor, ProcessarComandoUseCase processarComando,
                               RegistroMensagensProcessadas mensagensProcessadas, MetricasMensageria metricas) {
        this.leitor = leitor;
        this.processarComando = processarComando;
        this.mensagensProcessadas = mensagensProcessadas;
        this.metricas = metricas;
    }

    @Trace(dispatcher = true)
    public void processar(String corpo, Map<String, Object> cabecalhos) {
        NewRelic.getAgent().getTransaction()
            .acceptDistributedTraceHeaders(TransportType.Queue, CabecalhosNewRelic.de(cabecalhos));
        var comando = ler(corpo);
        NewRelic.setTransactionName("Mensageria", "Consumo/" + comando.tipo());
        CorrelacaoMensagem.registrar(comando.osId(), comando.sagaId(), comando.mensagemId(), comando.tipo());
        try {
            var resultado = executar(comando);
            metricas.registrarConsumo(comando.tipo(), resultado.rotulo());
            if (resultado == ResultadoProcessamento.DUPLICADA) {
                log.debug("Mensagem {} duplicada descartada", comando.mensagemId());
            } else {
                log.info("Comando {} processado: {}", comando.tipo(), resultado.rotulo());
            }
        } catch (RuntimeException e) {
            metricas.registrarConsumo(comando.tipo(), RESULTADO_ERRO);
            log.warn("Falha técnica ao processar {}; a mensagem volta à fila: {}", comando.tipo(), e.getMessage());
            throw e;
        } finally {
            CorrelacaoMensagem.limpar();
        }
    }

    private ComandoRecebido ler(String corpo) {
        try {
            return leitor.ler(corpo);
        } catch (MensagemInvalidaException e) {
            NewRelic.setTransactionName("Mensageria", "Consumo/Invalida");
            metricas.registrarConsumo(TIPO_DESCONHECIDO, RESULTADO_ERRO);
            log.error("Mensagem inválida recebida; segue para nova tentativa e DLQ: {}", e.getMessage());
            throw e;
        }
    }

    private ResultadoProcessamento executar(ComandoRecebido comando) {
        try {
            return processarComando.executar(comando);
        } catch (DuplicateKeyException e) {
            if (mensagensProcessadas.jaProcessada(comando.mensagemId())) {
                return ResultadoProcessamento.DUPLICADA;
            }
            throw e;
        }
    }
}
