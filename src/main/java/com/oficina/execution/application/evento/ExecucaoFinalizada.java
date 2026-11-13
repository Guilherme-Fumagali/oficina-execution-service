package com.oficina.execution.application.evento;

import java.time.Instant;
import java.util.UUID;

public record ExecucaoFinalizada(UUID mecanicoId, Instant finalizadaEm) implements EventoIntegracao {}
