package com.oficina.execution.domain.entity;

import com.oficina.execution.domain.valueobject.StatusExecucao;

import java.time.Instant;

public record HistoricoStatus(StatusExecucao de, StatusExecucao para, Instant em, String motivo) {}
