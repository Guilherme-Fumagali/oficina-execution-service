package com.oficina.execution.infrastructure.messaging;

import com.newrelic.api.agent.NewRelic;
import com.oficina.execution.application.evento.EventoIntegracao;
import com.oficina.execution.application.port.PublicadorEventos;
import com.oficina.execution.infrastructure.persistence.document.MensagemOutboxDocument;
import com.oficina.execution.infrastructure.persistence.document.MensagemOutboxDocument.StatusPublicacao;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
public class PublicadorOutbox implements PublicadorEventos {

    static final String ORIGEM = "execution-service";
    static final String FILA_OS_EVENTOS = "os-eventos";
    static final int VERSAO = 1;

    private final RepositorioOutbox outbox;
    private final SerializadorMensagens serializador;
    private final Clock relogio;

    public PublicadorOutbox(RepositorioOutbox outbox, SerializadorMensagens serializador, Clock relogio) {
        this.outbox = outbox;
        this.serializador = serializador;
        this.relogio = relogio;
    }

    @Override
    public void publicar(UUID osId, UUID sagaId, UUID causadaPor, EventoIntegracao evento) {
        var mensagemId = UUID.randomUUID();
        var agora = relogio.instant();
        var tipo = evento.getClass().getSimpleName();
        var envelope = new MensagemEnvelope(mensagemId, tipo, VERSAO, sagaId, osId, causadaPor, ORIGEM, agora,
            serializador.paraArvore(evento));
        var cabecalhos = new CabecalhosNewRelic();
        NewRelic.getAgent().getTransaction().insertDistributedTraceHeaders(cabecalhos);
        outbox.inserir(new MensagemOutboxDocument(mensagemId.toString(), FILA_OS_EVENTOS, tipo, VERSAO,
            osId.toString(), sagaId == null ? null : sagaId.toString(), serializador.escrever(envelope), cabecalhos.comoMapa(), StatusPublicacao.PENDENTE,
            agora, agora, null, 0, null));
    }
}
