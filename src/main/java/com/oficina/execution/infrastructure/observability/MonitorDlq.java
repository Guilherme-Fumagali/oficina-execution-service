package com.oficina.execution.infrastructure.observability;

import com.oficina.execution.infrastructure.messaging.PropriedadesMensageria;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class MonitorDlq {

    private static final Logger log = LoggerFactory.getLogger(MonitorDlq.class);

    static final String DLQ_MENSAGENS = "oficina.mensageria.dlq.mensagens";

    private final SqsAsyncClient sqs;
    private final String dlq;
    private final AtomicLong mensagens = new AtomicLong();

    public MonitorDlq(SqsAsyncClient sqs, PropriedadesMensageria propriedades, MeterRegistry registry) {
        this.sqs = sqs;
        this.dlq = propriedades.dlq();
        Gauge.builder(DLQ_MENSAGENS, mensagens, AtomicLong::get).tag("fila", dlq).register(registry);
    }

    @Scheduled(fixedDelayString = "${app.mensageria.dlq-intervalo:60000}")
    public void atualizar() {
        try {
            var url = sqs.getQueueUrl(r -> r.queueName(dlq)).join().queueUrl();
            var atributos = sqs.getQueueAttributes(r -> r.queueUrl(url)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)).join().attributes();
            mensagens.set(Long.parseLong(atributos.getOrDefault(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0")));
        } catch (RuntimeException e) {
            log.warn("Não foi possível consultar a DLQ {}: {}", dlq, e.getMessage());
        }
    }

    public long mensagensNaDlq() {
        return mensagens.get();
    }
}
