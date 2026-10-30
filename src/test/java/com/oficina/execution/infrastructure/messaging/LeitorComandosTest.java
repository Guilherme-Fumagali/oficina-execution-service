package com.oficina.execution.infrastructure.messaging;

import com.oficina.execution.application.comando.DadosComando;
import com.oficina.execution.domain.entity.ItemReparo;
import com.oficina.execution.domain.entity.Veiculo;
import com.oficina.execution.domain.valueobject.TipoItemReparo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitorComandosTest {

    private final LeitorComandos leitor = new LeitorComandos(new SerializadorMensagens());
    private final UUID mensagemId = UUID.randomUUID();
    private final UUID sagaId = UUID.randomUUID();
    private final UUID osId = UUID.randomUUID();

    private String envelope(String tipo, String dados) {
        return """
            {"mensagemId":"%s","tipo":"%s","versao":1,"sagaId":"%s","osId":"%s","causadaPor":null,
             "origem":"os-service","ocorridoEm":"2026-10-30T14:05:11.482Z","campoNovo":"ignorado","dados":%s}
            """.formatted(mensagemId, tipo, sagaId, osId, dados);
    }

    @Test
    void leIniciarDiagnosticoComVeiculo() {
        var clienteId = UUID.randomUUID();
        var comando = leitor.ler(envelope("IniciarDiagnostico", """
            {"clienteId":"%s","veiculo":{"placa":"ABC1D23","marca":"Fiat","modelo":"Argo","anoFabricacao":2021},
             "abertaEm":"2026-10-30T14:00:00Z"}""".formatted(clienteId)));

        assertThat(comando.mensagemId()).isEqualTo(mensagemId);
        assertThat(comando.sagaId()).isEqualTo(sagaId);
        assertThat(comando.osId()).isEqualTo(osId);
        assertThat(comando.tipo()).isEqualTo("IniciarDiagnostico");
        assertThat(comando.dados()).isEqualTo(new DadosComando.IniciarDiagnostico(clienteId,
            new Veiculo("ABC1D23", "Fiat", "Argo", 2021), Instant.parse("2026-10-30T14:00:00Z")));
    }

    @Test
    void iniciarDiagnosticoSemVeiculoChegaAoCasoDeUso() {
        var comando = leitor.ler(envelope("IniciarDiagnostico", "{}"));

        assertThat(comando.dados()).isEqualTo(new DadosComando.IniciarDiagnostico(null, null, null));
    }

    @Test
    void placaEmBrancoTornaDadosInvalidos() {
        var comando = leitor.ler(envelope("IniciarDiagnostico", """
            {"clienteId":"%s","veiculo":{"placa":" "}}""".formatted(UUID.randomUUID())));

        assertThat(comando.dados()).isInstanceOfSatisfying(DadosComando.Invalido.class,
            d -> assertThat(d.erro()).contains("placa"));
    }

    @Test
    void leIniciarReparoComItens() {
        var comando = leitor.ler(envelope("IniciarReparo", """
            {"itens":[{"tipo":"SERVICO","descricao":"Alinhamento","quantidade":1},
                      {"tipo":"PECA","descricao":"Pneu","quantidade":2}]}"""));

        assertThat(comando.dados()).isEqualTo(new DadosComando.IniciarReparo(List.of(
            new ItemReparo(TipoItemReparo.SERVICO, "Alinhamento", 1),
            new ItemReparo(TipoItemReparo.PECA, "Pneu", 2))));
    }

    @Test
    void iniciarReparoSemListaChegaAoCasoDeUso() {
        assertThat(leitor.ler(envelope("IniciarReparo", "null")).dados())
            .isEqualTo(new DadosComando.IniciarReparo(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"itens\":[{\"tipo\":\"MAO_DE_OBRA\",\"descricao\":\"x\",\"quantidade\":1}]}",
        "{\"itens\":[{\"tipo\":\"PECA\",\"descricao\":\"x\"}]}",
        "{\"itens\":[null]}",
        "{\"itens\":\"texto\"}"
    })
    void itensMalformadosTornamDadosInvalidos(String dados) {
        assertThat(leitor.ler(envelope("IniciarReparo", dados)).dados()).isInstanceOf(DadosComando.Invalido.class);
    }

    @Test
    void leMotivoDeReaberturaEDeCancelamento() {
        assertThat(leitor.ler(envelope("ReabrirDiagnostico", "{\"motivo\":\"OS sem itens\"}")).dados())
            .isEqualTo(new DadosComando.ReabrirDiagnostico("OS sem itens"));
        assertThat(leitor.ler(envelope("CancelarExecucao", "{\"motivo\":\"Reprovado\"}")).dados())
            .isEqualTo(new DadosComando.CancelarExecucao("Reprovado"));
        assertThat(leitor.ler(envelope("CancelarExecucao", "{}")).dados())
            .isEqualTo(new DadosComando.CancelarExecucao(null));
    }

    @Test
    void motivoComTipoErradoTornaDadosInvalidos() {
        assertThat(leitor.ler(envelope("CancelarExecucao", "{\"motivo\":{\"a\":1}}")).dados())
            .isInstanceOf(DadosComando.Invalido.class);
    }

    @Test
    void tipoDesconhecidoNaoEReconhecido() {
        assertThat(leitor.ler(envelope("GerarOrcamento", "{}")).dados()).isInstanceOf(DadosComando.NaoReconhecido.class);
    }

    @Test
    void envelopeSemIdentificacaoEInvalido() {
        assertThatThrownBy(() -> leitor.ler("{\"tipo\":\"IniciarDiagnostico\",\"osId\":\"%s\"}".formatted(osId)))
            .isInstanceOf(MensagemInvalidaException.class).hasMessageContaining("mensagemId");
        assertThatThrownBy(() -> leitor.ler("{\"mensagemId\":\"%s\",\"tipo\":\" \",\"osId\":\"%s\"}".formatted(mensagemId, osId)))
            .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> leitor.ler("{\"mensagemId\":\"%s\",\"osId\":\"%s\"}".formatted(mensagemId, osId)))
            .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> leitor.ler("{\"mensagemId\":\"%s\",\"tipo\":\"X\"}".formatted(mensagemId)))
            .isInstanceOf(MensagemInvalidaException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"não é json", "{\"mensagemId\":\"nao-uuid\"}", "[]"})
    void corpoQueNaoEEnvelopeEInvalido(String corpo) {
        assertThatThrownBy(() -> leitor.ler(corpo)).isInstanceOf(MensagemInvalidaException.class);
    }
}
