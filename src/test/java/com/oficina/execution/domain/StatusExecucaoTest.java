package com.oficina.execution.domain;

import com.oficina.execution.domain.exception.TransicaoInvalidaException;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.oficina.execution.domain.valueobject.StatusExecucao.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatusExecucaoTest {

    private static final Map<StatusExecucao, Set<StatusExecucao>> PERMITIDAS = Map.of(
        AGUARDANDO_DIAGNOSTICO, Set.of(EM_DIAGNOSTICO, CANCELADA),
        EM_DIAGNOSTICO, Set.of(DIAGNOSTICO_CONCLUIDO, CANCELADA),
        DIAGNOSTICO_CONCLUIDO, Set.of(EM_DIAGNOSTICO, AGUARDANDO_REPARO, CANCELADA),
        AGUARDANDO_REPARO, Set.of(EM_REPARO, FALHOU, CANCELADA),
        EM_REPARO, Set.of(FINALIZADA, FALHOU, CANCELADA),
        FINALIZADA, Set.of(),
        FALHOU, Set.of(),
        CANCELADA, Set.of());

    static Stream<Arguments> todosOsPares() {
        return Arrays.stream(StatusExecucao.values())
            .flatMap(de -> Arrays.stream(StatusExecucao.values()).map(para -> Arguments.of(de, para)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("todosOsPares")
    void tabelaDeTransicoesSegueAEspecificacao(StatusExecucao de, StatusExecucao para) {
        var permitida = PERMITIDAS.get(de).contains(para);

        assertThat(de.permiteTransicaoPara(para)).isEqualTo(permitida);
        if (permitida) {
            assertThatCode(() -> de.validarTransicaoPara(para)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> de.validarTransicaoPara(para))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Transição inválida: %s → %s".formatted(de, para));
        }
    }

    @ParameterizedTest
    @EnumSource(value = StatusExecucao.class, names = {"FINALIZADA", "FALHOU", "CANCELADA"})
    void statusTerminaisNaoAdmitemTransicao(StatusExecucao status) {
        assertThat(status.isTerminal()).isTrue();
        assertThat(StatusExecucao.naoTerminais()).doesNotContain(status);
    }

    @ParameterizedTest
    @EnumSource(value = StatusExecucao.class, names = {"FINALIZADA", "FALHOU", "CANCELADA"}, mode = EnumSource.Mode.EXCLUDE)
    void statusNaoTerminaisPodemSerCancelados(StatusExecucao status) {
        assertThat(status.isTerminal()).isFalse();
        assertThat(status.permiteTransicaoPara(CANCELADA)).isTrue();
    }
}
