package com.oficina.execution.infrastructure.web.dto.response;

import com.oficina.execution.domain.entity.Etapa;
import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.time.Instant;

public record EtapaClienteResponse(TipoEtapa tipo, Instant inicio, Instant fim) {

    public static EtapaClienteResponse from(Etapa etapa) {
        return new EtapaClienteResponse(etapa.tipo(), etapa.inicio(), etapa.fim());
    }
}
