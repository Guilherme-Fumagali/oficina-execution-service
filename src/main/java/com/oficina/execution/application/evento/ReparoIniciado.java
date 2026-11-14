package com.oficina.execution.application.evento;

import java.time.Instant;
import java.util.UUID;

public record ReparoIniciado(UUID mecanicoId, Instant iniciadoEm) implements EventoIntegracao {}
