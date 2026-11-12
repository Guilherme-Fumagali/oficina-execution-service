package com.oficina.execution.domain.exception;

import java.util.UUID;

public class RecursoNaoEncontradoException extends DomainException {
    public RecursoNaoEncontradoException(String recurso, UUID id) {
        super("%s não encontrada: %s".formatted(recurso, id));
    }
}
