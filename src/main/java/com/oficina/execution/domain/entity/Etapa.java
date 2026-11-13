package com.oficina.execution.domain.entity;

import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.time.Instant;
import java.util.UUID;

public record Etapa(TipoEtapa tipo, UUID mecanicoId, Instant inicio, Instant fim) {

    public boolean emAndamento() {
        return fim == null;
    }

    Etapa encerrar(Instant instante) {
        return new Etapa(tipo, mecanicoId, inicio, instante);
    }
}
