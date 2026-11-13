package com.oficina.execution.infrastructure.web.dto.response;

import com.oficina.execution.domain.entity.Apontamento;

import java.time.Instant;
import java.util.UUID;

public record ApontamentoResponse(UUID mecanicoId, String texto, Instant registradoEm) {

    public static ApontamentoResponse from(Apontamento apontamento) {
        return new ApontamentoResponse(apontamento.mecanicoId(), apontamento.texto(), apontamento.registradoEm());
    }
}
