package com.oficina.execution.domain.entity;

import com.oficina.execution.domain.exception.DomainException;

public record Veiculo(String placa, String marca, String modelo, Integer anoFabricacao) {

    public Veiculo {
        if (placa == null || placa.isBlank()) {
            throw new DomainException("A placa do veículo é obrigatória.");
        }
    }
}
