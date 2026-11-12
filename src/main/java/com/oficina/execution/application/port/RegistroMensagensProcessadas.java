package com.oficina.execution.application.port;

import java.time.Instant;
import java.util.UUID;

public interface RegistroMensagensProcessadas {

    boolean jaProcessada(UUID mensagemId);

    void registrar(UUID mensagemId, String tipo, Instant processadaEm);
}
