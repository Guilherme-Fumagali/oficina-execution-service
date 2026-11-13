package com.oficina.execution.infrastructure.web.dto.response;

import com.oficina.execution.domain.entity.HistoricoStatus;
import com.oficina.execution.domain.valueobject.StatusExecucao;

import java.time.Instant;

public record HistoricoStatusResponse(StatusExecucao de, StatusExecucao para, Instant em, String motivo) {

    public static HistoricoStatusResponse from(HistoricoStatus historico) {
        return new HistoricoStatusResponse(historico.de(), historico.para(), historico.em(), historico.motivo());
    }
}
