package com.oficina.execution.infrastructure.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class RelayOutbox {

    private final RepositorioOutbox outbox;
    private final EnvioMensagemOutbox envio;
    private final PropriedadesMensageria propriedades;
    private final Clock relogio;

    public RelayOutbox(RepositorioOutbox outbox, EnvioMensagemOutbox envio, PropriedadesMensageria propriedades,
                       Clock relogio) {
        this.outbox = outbox;
        this.envio = envio;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    @Scheduled(fixedDelayString = "${app.mensageria.relay.intervalo:500}")
    public void publicarPendentes() {
        var inicioDoCiclo = relogio.instant();
        for (int i = 0; i < propriedades.relay().lote(); i++) {
            var reservaAte = relogio.instant().plus(propriedades.relay().reserva());
            var proxima = outbox.reivindicarProxima(inicioDoCiclo, reservaAte);
            if (proxima.isEmpty()) {
                return;
            }
            envio.enviar(proxima.get());
        }
    }
}
