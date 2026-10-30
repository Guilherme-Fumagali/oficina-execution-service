package com.oficina.execution.application.port;

import com.oficina.execution.domain.entity.TransicaoExecucao;

public interface MetricasExecucao {

    void registrarTransicao(TransicaoExecucao transicao);
}
