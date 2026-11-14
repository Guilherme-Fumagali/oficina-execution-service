package com.oficina.execution.application;

import com.oficina.execution.application.port.UnidadeDeTrabalho;

import java.util.function.Supplier;

class UnidadeDeTrabalhoImediata implements UnidadeDeTrabalho {

    int execucoes;

    @Override
    public <T> T executar(Supplier<T> trabalho) {
        execucoes++;
        return trabalho.get();
    }

    @Override
    public void executar(Runnable trabalho) {
        execucoes++;
        trabalho.run();
    }
}
