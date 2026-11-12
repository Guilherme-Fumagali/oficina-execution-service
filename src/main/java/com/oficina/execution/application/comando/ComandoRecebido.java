package com.oficina.execution.application.comando;

import java.util.UUID;

public record ComandoRecebido(UUID mensagemId, String tipo, UUID sagaId, UUID osId, DadosComando dados) {}
