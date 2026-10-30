package com.oficina.execution.domain.entity;

import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.time.Duration;
import java.util.UUID;

public record TransicaoExecucao(
    UUID osId,
    StatusExecucao de,
    StatusExecucao para,
    TipoEtapa etapa,
    Duration duracaoEtapa
) {}
