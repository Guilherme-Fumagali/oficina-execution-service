package com.oficina.execution.domain.entity;

import com.oficina.execution.domain.exception.DomainException;
import com.oficina.execution.domain.valueobject.TipoItemReparo;

public record ItemReparo(TipoItemReparo tipo, String descricao, int quantidade) {

    public ItemReparo {
        if (tipo == null) {
            throw new DomainException("O tipo do item de reparo é obrigatório.");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new DomainException("A descrição do item de reparo é obrigatória.");
        }
        if (quantidade < 1) {
            throw new DomainException("A quantidade do item de reparo deve ser maior que zero.");
        }
    }
}
