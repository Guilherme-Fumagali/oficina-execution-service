package com.oficina.execution.application.evento;

public sealed interface EventoIntegracao permits ExecucaoEnfileirada, DiagnosticoIniciado, DiagnosticoConcluido,
    ReparoIniciado, ExecucaoFinalizada, ExecucaoFalhou, ExecucaoCancelada, ComandoRejeitado {
}
