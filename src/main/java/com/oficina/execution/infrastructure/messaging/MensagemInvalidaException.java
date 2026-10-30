package com.oficina.execution.infrastructure.messaging;

public class MensagemInvalidaException extends RuntimeException {

    public MensagemInvalidaException(String message) { super(message); }

    public MensagemInvalidaException(String message, Throwable cause) { super(message, cause); }
}
