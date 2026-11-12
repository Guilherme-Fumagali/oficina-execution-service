package com.oficina.execution.infrastructure.persistence;

import java.util.List;

public final class ColecoesMongo {

    public static final String EXECUCOES = "execucoes";
    public static final String OUTBOX = "outbox";
    public static final String MENSAGENS_PROCESSADAS = "mensagensProcessadas";
    public static final List<String> TODAS = List.of(EXECUCOES, OUTBOX, MENSAGENS_PROCESSADAS);

    private ColecoesMongo() {}
}
