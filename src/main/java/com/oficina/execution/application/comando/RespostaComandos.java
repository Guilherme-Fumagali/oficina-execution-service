package com.oficina.execution.application.comando;

import com.oficina.execution.application.evento.ComandoRejeitado;
import com.oficina.execution.application.evento.EventoIntegracao;
import com.oficina.execution.application.port.PublicadorEventos;

public class RespostaComandos {

    private final PublicadorEventos publicador;

    public RespostaComandos(PublicadorEventos publicador) {
        this.publicador = publicador;
    }

    public void responder(ComandoRecebido comando, EventoIntegracao evento) {
        publicador.publicar(comando.osId(), comando.sagaId(), comando.mensagemId(), evento);
    }

    public ResultadoProcessamento rejeitar(ComandoRecebido comando, CodigoRejeicao codigo, String motivo) {
        responder(comando, new ComandoRejeitado(comando.tipo(), motivo, codigo.name()));
        return ResultadoProcessamento.REJEITADA;
    }
}
