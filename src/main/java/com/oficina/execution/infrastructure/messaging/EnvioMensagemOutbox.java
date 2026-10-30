package com.oficina.execution.infrastructure.messaging;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.TransportType;
import com.oficina.execution.infrastructure.observability.MetricasMensageria;
import com.oficina.execution.infrastructure.persistence.document.MensagemOutboxDocument;
import io.awspring.cloud.sqs.operations.SqsOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;

@Component
public class EnvioMensagemOutbox {

    private static final Logger log = LoggerFactory.getLogger(EnvioMensagemOutbox.class);
    private static final int TAMANHO_MAXIMO_ERRO = 500;

    private final SqsOperations sqs;
    private final RepositorioOutbox outbox;
    private final PropriedadesMensageria propriedades;
    private final MetricasMensageria metricas;
    private final Clock relogio;

    public EnvioMensagemOutbox(SqsOperations sqs, RepositorioOutbox outbox, PropriedadesMensageria propriedades,
                               MetricasMensageria metricas, Clock relogio) {
        this.sqs = sqs;
        this.outbox = outbox;
        this.propriedades = propriedades;
        this.metricas = metricas;
        this.relogio = relogio;
    }

    @Trace(dispatcher = true)
    public void enviar(MensagemOutboxDocument mensagem) {
        NewRelic.setTransactionName("Mensageria", "Relay/" + mensagem.tipo());
        CorrelacaoMensagem.registrar(mensagem.osId(), mensagem.sagaId(), mensagem.id(), mensagem.tipo());
        try {
            var transacao = NewRelic.getAgent().getTransaction();
            transacao.acceptDistributedTraceHeaders(TransportType.Queue, CabecalhosNewRelic.de(mensagem.cabecalhos()));
            var cabecalhos = new CabecalhosNewRelic();
            transacao.insertDistributedTraceHeaders(cabecalhos);
            var atributos = new HashMap<String, Object>(cabecalhos.comoMapa());
            atributos.put("tipo", mensagem.tipo());
            atributos.put("versao", String.valueOf(mensagem.versao()));
            var fila = propriedades.nomeDaFila(mensagem.fila());
            sqs.<String>send(to -> to.queue(fila).payload(mensagem.payload()).headers(atributos));
            outbox.marcarPublicada(mensagem.id(), relogio.instant());
            metricas.registrarPublicacao(mensagem.tipo());
            log.info("Mensagem {} publicada na fila {}", mensagem.tipo(), fila);
        } catch (RuntimeException e) {
            outbox.registrarFalha(mensagem.id(), resumir(e), relogio.instant());
            log.warn("Falha ao publicar {} ({}); nova tentativa no próximo ciclo: {}",
                mensagem.tipo(), mensagem.id(), e.getMessage());
        } finally {
            CorrelacaoMensagem.limpar();
        }
    }

    private static String resumir(RuntimeException e) {
        var mensagem = e.getClass().getSimpleName() + ": " + e.getMessage();
        return mensagem.length() > TAMANHO_MAXIMO_ERRO ? mensagem.substring(0, TAMANHO_MAXIMO_ERRO) : mensagem;
    }
}
