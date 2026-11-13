package com.oficina.execution.application.evento;

import java.time.Instant;

public record ExecucaoCancelada(String motivo, Instant canceladaEm) implements EventoIntegracao {}
