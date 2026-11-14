package com.oficina.execution.infrastructure.web.dto.response;

import com.oficina.execution.domain.entity.Veiculo;

public record VeiculoResponse(String placa, String marca, String modelo, Integer anoFabricacao) {

    public static VeiculoResponse from(Veiculo veiculo) {
        return new VeiculoResponse(veiculo.placa(), veiculo.marca(), veiculo.modelo(), veiculo.anoFabricacao());
    }
}
