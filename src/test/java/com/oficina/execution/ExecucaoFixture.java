package com.oficina.execution;

import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.entity.ItemReparo;
import com.oficina.execution.domain.entity.Veiculo;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoItemReparo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ExecucaoFixture {

    public static final Instant T0 = Instant.parse("2026-10-30T12:00:00Z");
    public static final UUID MECANICO = UUID.fromString("6f1c4d2e-9a0b-4c3d-8e7f-1a2b3c4d5e6f");

    private ExecucaoFixture() {}

    public static Veiculo veiculo() {
        return new Veiculo("ABC1D23", "Toyota", "Corolla", 2022);
    }

    public static List<ItemReparo> itens() {
        return List.of(
            new ItemReparo(TipoItemReparo.SERVICO, "Troca de óleo", 1),
            new ItemReparo(TipoItemReparo.PECA, "Filtro de óleo", 2));
    }

    public static Execucao nova() {
        return Execucao.enfileirarDiagnostico(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), veiculo(), T0);
    }

    public static Execucao em(StatusExecucao status) {
        var execucao = nova();
        avancar(execucao, status);
        execucao.drenarTransicoes();
        return execucao;
    }

    private static void avancar(Execucao execucao, StatusExecucao alvo) {
        switch (alvo) {
            case AGUARDANDO_DIAGNOSTICO -> { }
            case EM_DIAGNOSTICO -> execucao.iniciarDiagnostico(MECANICO, T0.plusSeconds(60));
            case DIAGNOSTICO_CONCLUIDO -> {
                avancar(execucao, StatusExecucao.EM_DIAGNOSTICO);
                execucao.concluirDiagnostico("Vazamento no cárter", T0.plusSeconds(600));
            }
            case AGUARDANDO_REPARO -> {
                avancar(execucao, StatusExecucao.DIAGNOSTICO_CONCLUIDO);
                execucao.enfileirarReparo(itens(), T0.plusSeconds(3600));
            }
            case EM_REPARO -> {
                avancar(execucao, StatusExecucao.AGUARDANDO_REPARO);
                execucao.iniciarReparo(MECANICO, T0.plusSeconds(4000));
            }
            case FINALIZADA -> {
                avancar(execucao, StatusExecucao.EM_REPARO);
                execucao.concluirReparo(T0.plusSeconds(7600));
            }
            case FALHOU -> {
                avancar(execucao, StatusExecucao.EM_REPARO);
                execucao.registrarFalha("Peça incompatível", T0.plusSeconds(5000));
            }
            case CANCELADA -> execucao.cancelar("Orçamento reprovado", T0.plusSeconds(120));
        }
    }
}
