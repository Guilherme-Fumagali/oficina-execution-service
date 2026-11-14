package com.oficina.execution.infrastructure.config;

import io.awspring.cloud.sqs.support.converter.MessagingMessageConverter;
import io.awspring.cloud.sqs.support.converter.SqsMessagingMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.services.sqs.model.Message;

@Configuration
@EnableScheduling
public class MensageriaConfig {

    @Bean
    public MessagingMessageConverter<Message> conversorMensagensSqs() {
        var conversor = new SqsMessagingMessageConverter();
        conversor.doNotSendPayloadTypeHeader();
        return conversor;
    }
}
