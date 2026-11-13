package com.oficina.execution.application.comando;

import java.util.Locale;

public enum ResultadoProcessamento {
    APLICADA,
    IGNORADA,
    DUPLICADA,
    REJEITADA;

    public String rotulo() {
        return name().toLowerCase(Locale.ROOT);
    }
}
