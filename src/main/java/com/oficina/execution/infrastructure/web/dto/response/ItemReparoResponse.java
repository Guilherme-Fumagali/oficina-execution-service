package com.oficina.execution.infrastructure.web.dto.response;

import com.oficina.execution.domain.entity.ItemReparo;
import com.oficina.execution.domain.valueobject.TipoItemReparo;

public record ItemReparoResponse(TipoItemReparo tipo, String descricao, int quantidade) {

    public static ItemReparoResponse from(ItemReparo item) {
        return new ItemReparoResponse(item.tipo(), item.descricao(), item.quantidade());
    }
}
