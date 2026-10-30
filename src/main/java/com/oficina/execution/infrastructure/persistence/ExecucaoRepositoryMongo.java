package com.oficina.execution.infrastructure.persistence;

import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.repository.ExecucaoRepository;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;
import com.oficina.execution.infrastructure.persistence.document.ExecucaoDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ExecucaoRepositoryMongo implements ExecucaoRepository {

    private final MongoTemplate mongo;

    public ExecucaoRepositoryMongo(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Execucao salvar(Execucao execucao) {
        var salvo = mongo.save(ExecucaoDocumentMapper.paraDocumento(execucao));
        execucao.sincronizarVersao(salvo.getVersao());
        return execucao;
    }

    @Override
    public Optional<Execucao> buscarPorId(UUID osId) {
        return Optional.ofNullable(mongo.findById(osId.toString(), ExecucaoDocument.class))
            .map(ExecucaoDocumentMapper::paraDominio);
    }

    @Override
    public List<Execucao> listarFila(StatusExecucao status, TipoEtapa etapa) {
        var criterio = status != null
            ? Criteria.where("status").is(status)
            : Criteria.where("status").in(StatusExecucao.naoTerminais());
        if (etapa != null) {
            criterio = criterio.and("etapaAtual").is(etapa);
        }
        var consulta = Query.query(criterio)
            .with(Sort.by(Sort.Order.desc("prioridade"), Sort.Order.asc("enfileiradaEm")));
        return mongo.find(consulta, ExecucaoDocument.class).stream()
            .map(ExecucaoDocumentMapper::paraDominio)
            .toList();
    }

    @Override
    public long contarPorStatus(StatusExecucao status) {
        return mongo.count(Query.query(Criteria.where("status").is(status)), ExecucaoDocument.class);
    }
}
