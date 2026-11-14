package com.oficina.execution.application.evento;

import java.time.Instant;
import java.util.UUID;

public record DiagnosticoIniciado(UUID mecanicoId, Instant iniciadoEm) implements EventoIntegracao {}
