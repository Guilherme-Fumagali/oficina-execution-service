package com.oficina.execution.integracao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegracaoBase {

    protected static final String FILA_COMANDOS = "oficina-local-execution-comandos";
    protected static final String DLQ_COMANDOS = FILA_COMANDOS + "-dlq";
    protected static final String FILA_EVENTOS = "oficina-local-os-eventos";

    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:6.0.14"));
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3")).withServices(LocalStackContainer.Service.SQS);

    protected static final SqsClient SQS;
    protected static final ObjectMapper JSON = new ObjectMapper();
    private static final List<EventoRecebido> EVENTOS = new CopyOnWriteArrayList<>();

    protected record EventoRecebido(JsonNode envelope, Map<String, String> atributos) {

        public String tipo() { return envelope.get("tipo").asText(); }

        public String osId() { return envelope.get("osId").asText(); }

        public String causadaPor() {
            var valor = envelope.get("causadaPor");
            return valor == null || valor.isNull() ? null : valor.asText();
        }

        public JsonNode dados() { return envelope.get("dados"); }
    }

    static {
        MONGO.start();
        LOCALSTACK.start();
        SQS = SqsClient.builder()
            .endpointOverride(LOCALSTACK.getEndpoint())
            .region(Region.of(LOCALSTACK.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
            .build();
        criarFilas();
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.data.mongodb.uri",
            () -> MONGO.getReplicaSetUrl("execucao") + "?retryWrites=false&readPreference=secondaryPreferred");
        registro.add("spring.cloud.aws.sqs.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registro.add("spring.cloud.aws.region.static", LOCALSTACK::getRegion);
    }

    private static void criarFilas() {
        var dlq = SQS.createQueue(r -> r.queueName(DLQ_COMANDOS)).queueUrl();
        var arnDlq = SQS.getQueueAttributes(r -> r.queueUrl(dlq).attributeNames(QueueAttributeName.QUEUE_ARN))
            .attributes().get(QueueAttributeName.QUEUE_ARN);
        SQS.createQueue(r -> r.queueName(FILA_COMANDOS).attributes(Map.of(
            QueueAttributeName.VISIBILITY_TIMEOUT, "5",
            QueueAttributeName.REDRIVE_POLICY,
            "{\"deadLetterTargetArn\":\"" + arnDlq + "\",\"maxReceiveCount\":\"2\"}")));
        SQS.createQueue(r -> r.queueName(FILA_EVENTOS + "-dlq"));
        SQS.createQueue(r -> r.queueName(FILA_EVENTOS));
    }

    protected static String urlDaFila(String nome) {
        return SQS.getQueueUrl(r -> r.queueName(nome)).queueUrl();
    }

    protected static UUID enviarComando(String tipo, UUID osId, UUID sagaId, Object dados) {
        var mensagemId = UUID.randomUUID();
        enviarComando(mensagemId, tipo, osId, sagaId, dados);
        return mensagemId;
    }

    protected static void enviarComando(UUID mensagemId, String tipo, UUID osId, UUID sagaId, Object dados) {
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("mensagemId", mensagemId.toString());
        envelope.put("tipo", tipo);
        envelope.put("versao", 1);
        envelope.put("sagaId", sagaId == null ? null : sagaId.toString());
        envelope.put("osId", osId.toString());
        envelope.put("causadaPor", null);
        envelope.put("origem", "os-service");
        envelope.put("ocorridoEm", Instant.now().toString());
        envelope.put("dados", dados);
        try {
            enviarCorpo(JSON.writeValueAsString(envelope), tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static void enviarCorpo(String corpo, String tipo) {
        var url = urlDaFila(FILA_COMANDOS);
        SQS.sendMessage(r -> r.queueUrl(url).messageBody(corpo).messageAttributes(Map.of(
            "tipo", MessageAttributeValue.builder().dataType("String").stringValue(tipo).build(),
            "versao", MessageAttributeValue.builder().dataType("String").stringValue("1").build())));
    }

    protected static Map<String, Object> dadosIniciarDiagnostico(UUID clienteId) {
        return Map.of(
            "clienteId", clienteId.toString(),
            "veiculo", Map.of("placa", "BRA2E19", "marca", "Volkswagen", "modelo", "Gol", "anoFabricacao", 2019),
            "abertaEm", Instant.now().toString());
    }

    protected static synchronized void coletarEventos() {
        var url = urlDaFila(FILA_EVENTOS);
        var resposta = SQS.receiveMessage(r -> r.queueUrl(url).maxNumberOfMessages(10).waitTimeSeconds(1)
            .messageAttributeNames("All"));
        for (var mensagem : resposta.messages()) {
            try {
                var atributos = mensagem.messageAttributes().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stringValue()));
                EVENTOS.add(new EventoRecebido(JSON.readTree(mensagem.body()), atributos));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
            SQS.deleteMessage(r -> r.queueUrl(url).receiptHandle(mensagem.receiptHandle()));
        }
    }

    protected static List<EventoRecebido> eventos(UUID osId, String tipo) {
        return EVENTOS.stream().filter(e -> e.osId().equals(osId.toString()) && e.tipo().equals(tipo)).toList();
    }

    protected static List<EventoRecebido> eventos(UUID osId) {
        return EVENTOS.stream().filter(e -> e.osId().equals(osId.toString())).toList();
    }

    protected static List<EventoRecebido> aguardarEventos(UUID osId, String tipo, int quantidade) {
        await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(100)).until(() -> {
            coletarEventos();
            return eventos(osId, tipo).size() >= quantidade;
        });
        return eventos(osId, tipo);
    }

    protected static EventoRecebido aguardarEvento(UUID osId, String tipo) {
        return aguardarEventos(osId, tipo, 1).getFirst();
    }
}
