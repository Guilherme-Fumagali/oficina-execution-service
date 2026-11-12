package com.oficina.execution.domain.valueobject;

import com.oficina.execution.domain.exception.TransicaoInvalidaException;

import java.util.EnumSet;
import java.util.Set;

public enum StatusExecucao {
    AGUARDANDO_DIAGNOSTICO,
    EM_DIAGNOSTICO,
    DIAGNOSTICO_CONCLUIDO,
    AGUARDANDO_REPARO,
    EM_REPARO,
    FINALIZADA,
    FALHOU,
    CANCELADA;

    public static Set<StatusExecucao> naoTerminais() {
        return EnumSet.of(AGUARDANDO_DIAGNOSTICO, EM_DIAGNOSTICO, DIAGNOSTICO_CONCLUIDO, AGUARDANDO_REPARO, EM_REPARO);
    }

    public boolean isTerminal() {
        return !naoTerminais().contains(this);
    }

    public boolean permiteTransicaoPara(StatusExecucao destino) {
        return switch (this) {
            case AGUARDANDO_DIAGNOSTICO -> destino == EM_DIAGNOSTICO || destino == CANCELADA;
            case EM_DIAGNOSTICO -> destino == DIAGNOSTICO_CONCLUIDO || destino == CANCELADA;
            case DIAGNOSTICO_CONCLUIDO -> destino == EM_DIAGNOSTICO || destino == AGUARDANDO_REPARO || destino == CANCELADA;
            case AGUARDANDO_REPARO -> destino == EM_REPARO || destino == FALHOU || destino == CANCELADA;
            case EM_REPARO -> destino == FINALIZADA || destino == FALHOU || destino == CANCELADA;
            case FINALIZADA, FALHOU, CANCELADA -> false;
        };
    }

    public void validarTransicaoPara(StatusExecucao destino) {
        if (!permiteTransicaoPara(destino)) {
            throw new TransicaoInvalidaException("Transição inválida: %s → %s".formatted(this, destino));
        }
    }
}
