package com.oficina.execution.application.evento;

import java.time.Instant;
import java.util.UUID;

public record ExecucaoFalhou(UUID mecanicoId, String motivo, Instant ocorridaEm) implements EventoIntegracao {}
