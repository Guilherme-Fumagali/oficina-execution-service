package com.oficina.execution.domain.entity;

import java.time.Instant;
import java.util.UUID;

public record Apontamento(UUID mecanicoId, String texto, Instant registradoEm) {}
