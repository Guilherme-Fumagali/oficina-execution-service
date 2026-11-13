package com.oficina.execution.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InicializadorMongo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorMongo.class);

    static final String INDICE_FILA = "idx_execucoes_fila";
    static final String INDICE_CLIENTE = "idx_execucoes_cliente";
    static final String INDICE_OUTBOX = "idx_outbox_pendentes";
    static final String INDICE_INBOX_TTL = "idx_mensagens_processadas_ttl";
    static final Duration RETENCAO_MENSAGENS_PROCESSADAS = Duration.ofDays(14);

    private final MongoTemplate mongo;

    public InicializadorMongo(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        ColecoesMongo.TODAS.forEach(this::criarColecao);
        mongo.indexOps(ColecoesMongo.EXECUCOES).ensureIndex(new Index()
            .on("status", Sort.Direction.ASC)
            .on("prioridade", Sort.Direction.DESC)
            .on("enfileiradaEm", Sort.Direction.ASC)
            .named(INDICE_FILA));
        mongo.indexOps(ColecoesMongo.EXECUCOES).ensureIndex(new Index()
            .on("clienteId", Sort.Direction.ASC)
            .named(INDICE_CLIENTE));
        mongo.indexOps(ColecoesMongo.OUTBOX).ensureIndex(new Index()
            .on("status", Sort.Direction.ASC)
            .on("criadaEm", Sort.Direction.ASC)
            .named(INDICE_OUTBOX));
        mongo.indexOps(ColecoesMongo.MENSAGENS_PROCESSADAS).ensureIndex(new Index()
            .on("processadaEm", Sort.Direction.ASC)
            .expire(RETENCAO_MENSAGENS_PROCESSADAS)
            .named(INDICE_INBOX_TTL));
        log.info("Coleções e índices do MongoDB verificados: {}", ColecoesMongo.TODAS);
    }

    private void criarColecao(String nome) {
        if (mongo.collectionExists(nome)) {
            return;
        }
        try {
            mongo.createCollection(nome);
            log.info("Coleção {} criada", nome);
        } catch (DataAccessException e) {
            if (!mongo.collectionExists(nome)) {
                throw e;
            }
        }
    }
}
