package com.oficina.execution.infrastructure.persistence;

import com.oficina.execution.application.port.RegistroMensagensProcessadas;
import com.oficina.execution.infrastructure.persistence.document.MensagemProcessadaDocument;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class MensagensProcessadasMongo implements RegistroMensagensProcessadas {

    private final MongoTemplate mongo;

    public MensagensProcessadasMongo(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean jaProcessada(UUID mensagemId) {
        return mongo.exists(Query.query(Criteria.where("_id").is(mensagemId.toString())), MensagemProcessadaDocument.class);
    }

    @Override
    public void registrar(UUID mensagemId, String tipo, Instant processadaEm) {
        mongo.insert(new MensagemProcessadaDocument(mensagemId.toString(), tipo, processadaEm));
    }
}
