package com.oficina.execution.application.evento;

import java.time.Instant;
import java.util.UUID;

public record DiagnosticoConcluido(UUID mecanicoId, Instant concluidoEm, String observacoes) implements EventoIntegracao {}
