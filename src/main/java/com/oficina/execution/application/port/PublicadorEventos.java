package com.oficina.execution.application.port;

import com.oficina.execution.application.evento.EventoIntegracao;

import java.util.UUID;

public interface PublicadorEventos {

    void publicar(UUID osId, UUID sagaId, UUID causadaPor, EventoIntegracao evento);
}
