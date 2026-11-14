package com.oficina.execution.application.evento;

import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.time.Instant;

public record ExecucaoEnfileirada(TipoEtapa etapa, Instant enfileiradaEm) implements EventoIntegracao {}
