package com.oficina.execution.infrastructure.messaging;

import com.oficina.execution.infrastructure.persistence.document.MensagemOutboxDocument;
import com.oficina.execution.infrastructure.persistence.document.MensagemOutboxDocument.StatusPublicacao;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class RepositorioOutbox {

    private final MongoTemplate mongo;

    public RepositorioOutbox(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void inserir(MensagemOutboxDocument mensagem) {
        mongo.insert(mensagem);
    }

    public Optional<MensagemOutboxDocument> reivindicarProxima(Instant agora, Instant reservadaAte) {
        var pendente = Query.query(Criteria.where("status").is(StatusPublicacao.PENDENTE)
                .and("reservadaAte").lt(agora))
            .with(Sort.by(Sort.Direction.ASC, "criadaEm"));
        return Optional.ofNullable(mongo.findAndModify(pendente,
            new Update().set("reservadaAte", reservadaAte),
            FindAndModifyOptions.options().returnNew(true),
            MensagemOutboxDocument.class));
    }

    public void marcarPublicada(String id, Instant publicadaEm) {
        mongo.updateFirst(porId(id), new Update()
            .set("status", StatusPublicacao.PUBLICADA)
            .set("publicadaEm", publicadaEm), MensagemOutboxDocument.class);
    }

    public void registrarFalha(String id, String erro, Instant liberadaEm) {
        mongo.updateFirst(porId(id), new Update()
            .inc("tentativas", 1)
            .set("ultimoErro", erro)
            .set("reservadaAte", liberadaEm), MensagemOutboxDocument.class);
    }

    private static Query porId(String id) {
        return Query.query(Criteria.where("_id").is(id));
    }
}
