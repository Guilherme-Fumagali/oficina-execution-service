package com.oficina.execution.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

@Component
public class SerializadorMensagens {

    private final ObjectMapper mapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    public String escrever(MensagemEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar a mensagem " + envelope.tipo(), e);
        }
    }

    public JsonNode paraArvore(Object valor) {
        return mapper.valueToTree(valor);
    }

    public MensagemEnvelope lerEnvelope(String corpo) {
        try {
            return mapper.readValue(corpo, MensagemEnvelope.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new MensagemInvalidaException("Corpo da mensagem não é um envelope JSON válido.", e);
        }
    }

    public <T> T converter(JsonNode dados, Class<T> tipo) throws JsonProcessingException {
        return mapper.treeToValue(dados, tipo);
    }
}
