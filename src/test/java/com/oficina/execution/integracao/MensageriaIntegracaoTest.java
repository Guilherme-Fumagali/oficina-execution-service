package com.oficina.execution.integracao;

import com.oficina.execution.domain.repository.ExecucaoRepository;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.infrastructure.messaging.RepositorioOutbox;
import com.oficina.execution.infrastructure.observability.MonitorDlq;
import com.oficina.execution.infrastructure.persistence.ColecoesMongo;
import com.oficina.execution.infrastructure.persistence.document.MensagemOutboxDocument;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MensageriaIntegracaoTest extends IntegracaoBase {

    @Autowired ExecucaoRepository repository;
    @Autowired MongoTemplate mongo;
    @Autowired MeterRegistry registry;
    @Autowired MonitorDlq monitorDlq;
    @Autowired RepositorioOutbox outbox;

    @Test
    void comandoNaFilaCriaDocumentoEPublicaExecucaoEnfileirada() {
        var osId = UUID.randomUUID();
        var sagaId = UUID.randomUUID();
        var clienteId = UUID.randomUUID();

        var mensagemId = enviarComando("IniciarDiagnostico", osId, sagaId, dadosIniciarDiagnostico(clienteId));

        var evento = aguardarEvento(osId, "ExecucaoEnfileirada");
        var execucao = repository.buscarPorId(osId).orElseThrow();
        assertThat(execucao.getStatus()).isEqualTo(StatusExecucao.AGUARDANDO_DIAGNOSTICO);
        assertThat(execucao.getClienteId()).isEqualTo(clienteId);
        assertThat(execucao.getSagaId()).isEqualTo(sagaId);
        assertThat(execucao.getVeiculo().placa()).isEqualTo("BRA2E19");

        var envelope = evento.envelope();
        assertThat(UUID.fromString(envelope.get("mensagemId").asText())).isNotEqualTo(mensagemId);
        assertThat(envelope.get("versao").asInt()).isEqualTo(1);
        assertThat(envelope.get("sagaId").asText()).isEqualTo(sagaId.toString());
        assertThat(evento.causadaPor()).isEqualTo(mensagemId.toString());
        assertThat(envelope.get("origem").asText()).isEqualTo("execution-service");
        assertThat(envelope.get("ocorridoEm").asText()).endsWith("Z");
        assertThat(evento.dados().get("etapa").asText()).isEqualTo("DIAGNOSTICO");
        assertThat(Instant.parse(evento.dados().get("enfileiradaEm").asText())).isEqualTo(execucao.getEnfileiradaEm());
        assertThat(evento.atributos()).containsEntry("tipo", "ExecucaoEnfileirada").containsEntry("versao", "1")
            .doesNotContainKey("JavaType");

        var outboxGravado = mongo.findById(envelope.get("mensagemId").asText(), MensagemOutboxDocument.class);
        assertThat(outboxGravado).isNotNull();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
            mongo.findById(envelope.get("mensagemId").asText(), MensagemOutboxDocument.class).status())
            .isEqualTo(MensagemOutboxDocument.StatusPublicacao.PUBLICADA));
        assertThat(mongo.exists(Query.query(Criteria.where("_id").is(mensagemId.toString())),
            ColecoesMongo.MENSAGENS_PROCESSADAS)).isTrue();
    }

    @Test
    void mensagemDuplicadaEDescartada() {
        var osId = UUID.randomUUID();
        var mensagemId = UUID.randomUUID();
        var duplicadasAntes = consumidas("IniciarDiagnostico", "duplicada");

        enviarComando(mensagemId, "IniciarDiagnostico", osId, UUID.randomUUID(), dadosIniciarDiagnostico(UUID.randomUUID()));
        aguardarEvento(osId, "ExecucaoEnfileirada");
        enviarComando(mensagemId, "IniciarDiagnostico", osId, UUID.randomUUID(), dadosIniciarDiagnostico(UUID.randomUUID()));

        await().atMost(Duration.ofSeconds(30))
            .until(() -> consumidas("IniciarDiagnostico", "duplicada") > duplicadasAntes);
        coletarEventos();
        assertThat(eventos(osId, "ExecucaoEnfileirada")).hasSize(1);
        assertThat(mongo.count(Query.query(Criteria.where("_id").is(mensagemId.toString())),
            ColecoesMongo.MENSAGENS_PROCESSADAS)).isEqualTo(1);
    }

    @Test
    void comandoReenviadoComNovoIdRecebeAMesmaResposta() {
        var osId = UUID.randomUUID();
        var sagaId = UUID.randomUUID();
        var primeiro = enviarComando("IniciarDiagnostico", osId, sagaId, dadosIniciarDiagnostico(UUID.randomUUID()));
        aguardarEvento(osId, "ExecucaoEnfileirada");

        var reenvio = enviarComando("IniciarDiagnostico", osId, sagaId, dadosIniciarDiagnostico(UUID.randomUUID()));

        var respostas = aguardarEventos(osId, "ExecucaoEnfileirada", 2);
        assertThat(respostas).extracting(EventoRecebido::causadaPor)
            .containsExactlyInAnyOrder(primeiro.toString(), reenvio.toString());
        assertThat(respostas).extracting(e -> e.dados().get("enfileiradaEm").asText()).containsOnly(
            respostas.getFirst().dados().get("enfileiradaEm").asText());
        assertThat(mongo.count(Query.query(Criteria.where("_id").is(osId.toString())), ColecoesMongo.EXECUCOES))
            .isEqualTo(1);
    }

    @Test
    void comandoInvalidoParaOEstadoRespondeComandoRejeitado() {
        var osId = UUID.randomUUID();

        var mensagemId = enviarComando("CancelarExecucao", osId, UUID.randomUUID(), Map.of("motivo", "Orçamento reprovado"));

        var rejeicao = aguardarEvento(osId, "ComandoRejeitado");
        assertThat(rejeicao.causadaPor()).isEqualTo(mensagemId.toString());
        assertThat(rejeicao.dados().get("comando").asText()).isEqualTo("CancelarExecucao");
        assertThat(rejeicao.dados().get("codigo").asText()).isEqualTo("EXECUCAO_INEXISTENTE");
        assertThat(rejeicao.dados().get("motivo").asText()).contains(osId.toString());
    }

    @Test
    void comandoDesconhecidoEDadosInvalidosSaoRejeitados() {
        var osId = UUID.randomUUID();

        enviarComando("GerarOrcamento", osId, UUID.randomUUID(), Map.of());
        enviarComando("IniciarReparo", osId, UUID.randomUUID(),
            Map.of("itens", List.of(Map.of("tipo", "MAO_DE_OBRA", "descricao", "x", "quantidade", 1))));

        var rejeicoes = aguardarEventos(osId, "ComandoRejeitado", 2);
        assertThat(rejeicoes).extracting(e -> e.dados().get("codigo").asText())
            .containsExactlyInAnyOrder("COMANDO_DESCONHECIDO", "DADOS_INVALIDOS");
    }

    @Test
    void cancelamentoPorComandoEIdempotente() {
        var osId = UUID.randomUUID();
        var sagaId = UUID.randomUUID();
        enviarComando("IniciarDiagnostico", osId, sagaId, dadosIniciarDiagnostico(UUID.randomUUID()));
        aguardarEvento(osId, "ExecucaoEnfileirada");

        enviarComando("CancelarExecucao", osId, sagaId, Map.of("motivo", "Orçamento reprovado"));
        var primeira = aguardarEvento(osId, "ExecucaoCancelada");
        enviarComando("CancelarExecucao", osId, sagaId, Map.of("motivo", "Reenvio por prazo"));

        var respostas = aguardarEventos(osId, "ExecucaoCancelada", 2);
        assertThat(respostas).extracting(e -> e.dados().get("motivo").asText()).containsOnly("Orçamento reprovado");
        assertThat(respostas).extracting(e -> e.dados().get("canceladaEm").asText())
            .containsOnly(primeira.dados().get("canceladaEm").asText());
        assertThat(repository.buscarPorId(osId).orElseThrow().getStatus()).isEqualTo(StatusExecucao.CANCELADA);
    }

    @Test
    void mensagemMalformadaVaiParaDlqEAtualizaOGauge() {
        var urlDlq = urlDaFila(DLQ_COMANDOS);
        var antes = mensagensNaFila(urlDlq);

        enviarCorpo("isto não é um envelope", "IniciarDiagnostico");

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1))
            .until(() -> mensagensNaFila(urlDlq) > antes);
        monitorDlq.atualizar();
        assertThat(monitorDlq.mensagensNaDlq()).isGreaterThan(antes);
        assertThat(registry.get("oficina.mensageria.dlq.mensagens").tag("fila", DLQ_COMANDOS).gauge().value())
            .isGreaterThan(antes);
        assertThat(consumidas("desconhecido", "erro")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void falhaDeEnvioMantemMensagemPendenteComTentativa() {
        var id = UUID.randomUUID().toString();
        var agora = Instant.now();
        outbox.inserir(new MensagemOutboxDocument(id, "fila-inexistente", "ExecucaoEnfileirada", 1,
            UUID.randomUUID().toString(), null, "{}", Map.of(), MensagemOutboxDocument.StatusPublicacao.PENDENTE,
            agora, agora, null, 0, null));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var gravada = mongo.findById(id, MensagemOutboxDocument.class);
            assertThat(gravada.tentativas()).isPositive();
            assertThat(gravada.status()).isEqualTo(MensagemOutboxDocument.StatusPublicacao.PENDENTE);
            assertThat(gravada.ultimoErro()).contains("fila-inexistente");
        });
        mongo.remove(Query.query(Criteria.where("_id").is(id)), MensagemOutboxDocument.class);
    }

    private long mensagensNaFila(String url) {
        var atributos = SQS.getQueueAttributes(r -> r.queueUrl(url)
            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)).attributes();
        return Long.parseLong(atributos.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
    }

    private double consumidas(String tipo, String resultado) {
        var contador = registry.find("oficina.mensageria.consumidas").tags("tipo", tipo, "resultado", resultado).counter();
        return contador == null ? 0 : contador.count();
    }
}
