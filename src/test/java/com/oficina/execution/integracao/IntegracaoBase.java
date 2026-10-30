package com.oficina.execution.integracao;

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
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Map;

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
}
