package com.oficina.execution.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MetricasMensageria {

    static final String PUBLICADAS = "oficina.mensageria.publicadas";
    static final String CONSUMIDAS = "oficina.mensageria.consumidas";

    private final MeterRegistry registry;

    public MetricasMensageria(MeterRegistry registry) {
        this.registry = registry;
    }

    public void registrarPublicacao(String tipo) {
        registry.counter(PUBLICADAS, "tipo", tipo).increment();
    }

    public void registrarConsumo(String tipo, String resultado) {
        registry.counter(CONSUMIDAS, "tipo", tipo, "resultado", resultado).increment();
    }
}
