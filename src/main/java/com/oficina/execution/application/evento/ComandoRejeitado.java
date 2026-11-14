package com.oficina.execution.application.evento;

public record ComandoRejeitado(String comando, String motivo, String codigo) implements EventoIntegracao {}
