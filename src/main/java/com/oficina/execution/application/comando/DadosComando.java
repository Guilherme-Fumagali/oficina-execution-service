package com.oficina.execution.application.comando;

import com.oficina.execution.domain.entity.ItemReparo;
import com.oficina.execution.domain.entity.Veiculo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public sealed interface DadosComando {

    record IniciarDiagnostico(UUID clienteId, Veiculo veiculo, Instant abertaEm) implements DadosComando {}

    record ReabrirDiagnostico(String motivo) implements DadosComando {}

    record IniciarReparo(List<ItemReparo> itens) implements DadosComando {}

    record CancelarExecucao(String motivo) implements DadosComando {}

    record NaoReconhecido() implements DadosComando {}

    record Invalido(String erro) implements DadosComando {}
}
