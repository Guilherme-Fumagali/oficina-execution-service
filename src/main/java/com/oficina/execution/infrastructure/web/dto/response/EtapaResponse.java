package com.oficina.execution.infrastructure.web.dto.response;

import com.oficina.execution.domain.entity.Etapa;
import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.time.Instant;
import java.util.UUID;

public record EtapaResponse(TipoEtapa tipo, UUID mecanicoId, Instant inicio, Instant fim) {

    public static EtapaResponse from(Etapa etapa) {
        return new EtapaResponse(etapa.tipo(), etapa.mecanicoId(), etapa.inicio(), etapa.fim());
    }
}
