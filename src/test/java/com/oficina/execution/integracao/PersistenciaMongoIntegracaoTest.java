package com.oficina.execution.integracao;

import com.oficina.execution.application.port.RegistroMensagensProcessadas;
import com.oficina.execution.application.port.UnidadeDeTrabalho;
import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.repository.ExecucaoRepository;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;
import com.oficina.execution.infrastructure.persistence.ColecoesMongo;
import com.oficina.execution.infrastructure.persistence.InicializadorMongo;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.oficina.execution.ExecucaoFixture.MECANICO;
import static com.oficina.execution.ExecucaoFixture.itens;
import static com.oficina.execution.ExecucaoFixture.veiculo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenciaMongoIntegracaoTest extends IntegracaoBase {

    @Autowired MongoTemplate mongo;
    @Autowired ExecucaoRepository repository;
    @Autowired UnidadeDeTrabalho unidadeDeTrabalho;
    @Autowired RegistroMensagensProcessadas mensagensProcessadas;
    @Autowired InicializadorMongo inicializador;

    private Execucao nova(Instant enfileiradaEm) {
        return Execucao.enfileirarDiagnostico(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), veiculo(), enfileiradaEm);
    }

    @Test
    void colecoesEIndicesSaoCriadosNaSubida() {
        assertThat(mongo.getCollectionNames()).containsAll(ColecoesMongo.TODAS);

        var fila = indice(ColecoesMongo.EXECUCOES, "idx_execucoes_fila");
        assertThat(fila.getIndexFields()).extracting(f -> f.getKey() + ":" + f.getDirection())
            .containsExactly("status:ASC", "prioridade:DESC", "enfileiradaEm:ASC");
        assertThat(indice(ColecoesMongo.EXECUCOES, "idx_execucoes_cliente").getIndexFields())
            .extracting(f -> f.getKey()).containsExactly("clienteId");
        assertThat(indice(ColecoesMongo.OUTBOX, "idx_outbox_pendentes").getIndexFields())
            .extracting(f -> f.getKey()).containsExactly("status", "criadaEm");
        var ttl = indice(ColecoesMongo.MENSAGENS_PROCESSADAS, "idx_mensagens_processadas_ttl");
        assertThat(ttl.getIndexFields()).extracting(f -> f.getKey()).containsExactly("processadaEm");
        assertThat(ttl.getExpireAfter()).contains(Duration.ofDays(14));
    }

    @Test
    void inicializacaoEIdempotente() {
        inicializador.run(null);
        inicializador.run(null);

        assertThat(mongo.indexOps(ColecoesMongo.EXECUCOES).getIndexInfo()).hasSize(3);
    }

    @Test
    void agregadoCompletoEGravadoEmUmDocumento() {
        var execucao = nova(Instant.parse("2026-10-30T08:00:00Z"));
        execucao.iniciarDiagnostico(MECANICO, Instant.parse("2026-10-30T08:10:00Z"));
        execucao.registrarApontamento(MECANICO, "Ruído na suspensão dianteira", Instant.parse("2026-10-30T08:20:00Z"));
        execucao.concluirDiagnostico("Bucha da bandeja gasta", Instant.parse("2026-10-30T08:40:00Z"));
        execucao.enfileirarReparo(itens(), Instant.parse("2026-10-30T09:00:00Z"));
        execucao.alterarPrioridade(3);

        repository.salvar(execucao);
        var recuperada = repository.buscarPorId(execucao.getOsId()).orElseThrow();

        assertThat(recuperada.getVersao()).isZero();
        assertThat(execucao.getVersao()).isZero();
        assertThat(recuperada.getStatus()).isEqualTo(StatusExecucao.AGUARDANDO_REPARO);
        assertThat(recuperada.getEtapaAtual()).isEqualTo(TipoEtapa.REPARO);
        assertThat(recuperada.getPrioridade()).isEqualTo(3);
        assertThat(recuperada.getVeiculo()).isEqualTo(veiculo());
        assertThat(recuperada.getSagaId()).isEqualTo(execucao.getSagaId());
        assertThat(recuperada.getClienteId()).isEqualTo(execucao.getClienteId());
        assertThat(recuperada.getEnfileiradaEm()).isEqualTo(Instant.parse("2026-10-30T09:00:00Z"));
        assertThat(recuperada.getItensReparo()).containsExactlyElementsOf(itens());
        assertThat(recuperada.getEtapas()).containsExactlyElementsOf(execucao.getEtapas());
        assertThat(recuperada.getApontamentos()).containsExactlyElementsOf(execucao.getApontamentos());
        assertThat(recuperada.getHistoricoStatus()).containsExactlyElementsOf(execucao.getHistoricoStatus());
        assertThat(recuperada.getObservacoesDiagnostico()).isEqualTo("Bucha da bandeja gasta");

        var bruto = mongo.getCollection(ColecoesMongo.EXECUCOES)
            .find(new Document("_id", execucao.getOsId().toString())).first();
        assertThat(bruto).isNotNull().doesNotContainKey("_class").containsEntry("versao", 0L);
        assertThat(bruto.get("veiculo", Document.class)).containsEntry("placa", "ABC1D23");
    }

    @Test
    void lockOtimistaRecusaAtualizacaoConcorrente() {
        var execucao = nova(Instant.parse("2026-10-30T10:00:00Z"));
        repository.salvar(execucao);
        var primeira = repository.buscarPorId(execucao.getOsId()).orElseThrow();
        var segunda = repository.buscarPorId(execucao.getOsId()).orElseThrow();

        primeira.alterarPrioridade(5);
        repository.salvar(primeira);
        segunda.iniciarDiagnostico(MECANICO, Instant.parse("2026-10-30T10:05:00Z"));

        assertThatThrownBy(() -> repository.salvar(segunda)).isInstanceOf(OptimisticLockingFailureException.class);
        var gravada = repository.buscarPorId(execucao.getOsId()).orElseThrow();
        assertThat(gravada.getPrioridade()).isEqualTo(5);
        assertThat(gravada.getStatus()).isEqualTo(StatusExecucao.AGUARDANDO_DIAGNOSTICO);
        assertThat(gravada.getVersao()).isEqualTo(1L);
    }

    @Test
    void filaOrdenadaPorPrioridadeEDepoisPorEntrada() {
        var base = Instant.parse("2020-01-01T00:00:00Z");
        var antigaSemPrioridade = nova(base);
        var recenteSemPrioridade = nova(base.plusSeconds(60));
        var recenteUrgente = nova(base.plusSeconds(120));
        var antigaUrgente = nova(base.plusSeconds(30));
        recenteUrgente.alterarPrioridade(9);
        antigaUrgente.alterarPrioridade(9);
        var ids = java.util.List.of(antigaSemPrioridade, recenteSemPrioridade, recenteUrgente, antigaUrgente);
        ids.forEach(repository::salvar);
        var esperados = ids.stream().map(Execucao::getOsId).collect(Collectors.toSet());

        var fila = repository.listarFila(StatusExecucao.AGUARDANDO_DIAGNOSTICO, TipoEtapa.DIAGNOSTICO).stream()
            .map(Execucao::getOsId).filter(esperados::contains).toList();

        assertThat(fila).containsExactly(antigaUrgente.getOsId(), recenteUrgente.getOsId(),
            antigaSemPrioridade.getOsId(), recenteSemPrioridade.getOsId());
        assertThat(repository.contarPorStatus(StatusExecucao.AGUARDANDO_DIAGNOSTICO)).isGreaterThanOrEqualTo(4);
    }

    @Test
    void unidadeDeTrabalhoDesfazTudoQuandoFalha() {
        var execucao = nova(Instant.parse("2026-10-30T11:00:00Z"));
        var mensagemId = UUID.randomUUID();

        assertThatThrownBy(() -> unidadeDeTrabalho.executar(() -> {
            assertThat(mensagensProcessadas.jaProcessada(mensagemId)).isFalse();
            mensagensProcessadas.registrar(mensagemId, "IniciarDiagnostico", Instant.now());
            repository.salvar(execucao);
            assertThat(repository.buscarPorId(execucao.getOsId())).isPresent();
            throw new IllegalStateException("falha simulada depois das gravações");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repository.buscarPorId(execucao.getOsId())).isEmpty();
        assertThat(mensagensProcessadas.jaProcessada(mensagemId)).isFalse();
    }

    @Test
    void unidadeDeTrabalhoConfirmaGravacoes() {
        var execucao = nova(Instant.parse("2026-10-30T12:00:00Z"));
        var mensagemId = UUID.randomUUID();

        unidadeDeTrabalho.executar(() -> {
            assertThat(repository.buscarPorId(execucao.getOsId())).isEmpty();
            mensagensProcessadas.registrar(mensagemId, "IniciarDiagnostico", Instant.now());
            repository.salvar(execucao);
            assertThat(mensagensProcessadas.jaProcessada(mensagemId)).isTrue();
        });

        assertThat(repository.buscarPorId(execucao.getOsId())).isPresent();
        assertThat(mensagensProcessadas.jaProcessada(mensagemId)).isTrue();
    }


    private IndexInfo indice(String colecao, String nome) {
        Map<String, IndexInfo> porNome = mongo.indexOps(colecao).getIndexInfo().stream()
            .collect(Collectors.toMap(IndexInfo::getName, i -> i));
        assertThat(porNome).containsKey(nome);
        return porNome.get(nome);
    }
}
