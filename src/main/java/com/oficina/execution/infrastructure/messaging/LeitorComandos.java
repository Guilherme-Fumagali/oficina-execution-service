package com.oficina.execution.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.oficina.execution.application.comando.ComandoRecebido;
import com.oficina.execution.application.comando.DadosComando;
import com.oficina.execution.domain.entity.ItemReparo;
import com.oficina.execution.domain.entity.Veiculo;
import com.oficina.execution.domain.exception.DomainException;
import com.oficina.execution.domain.valueobject.TipoItemReparo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class LeitorComandos {

    record VeiculoJson(String placa, String marca, String modelo, Integer anoFabricacao) {}

    record IniciarDiagnosticoJson(UUID clienteId, VeiculoJson veiculo, Instant abertaEm) {}

    record ItemJson(TipoItemReparo tipo, String descricao, Integer quantidade) {}

    record IniciarReparoJson(List<ItemJson> itens) {}

    record MotivoJson(String motivo) {}

    private final SerializadorMensagens serializador;

    public LeitorComandos(SerializadorMensagens serializador) {
        this.serializador = serializador;
    }

    public ComandoRecebido ler(String corpo) {
        var envelope = serializador.lerEnvelope(corpo);
        if (envelope.mensagemId() == null || envelope.osId() == null
                || envelope.tipo() == null || envelope.tipo().isBlank()) {
            throw new MensagemInvalidaException("Envelope sem mensagemId, tipo ou osId.");
        }
        return new ComandoRecebido(envelope.mensagemId(), envelope.tipo(), envelope.sagaId(), envelope.osId(),
            lerDados(envelope.tipo(), envelope.dados()));
    }

    private DadosComando lerDados(String tipo, JsonNode dados) {
        var conteudo = dados == null || dados.isNull() ? serializador.paraArvore(Map.of()) : dados;
        try {
            return switch (tipo) {
                case "IniciarDiagnostico" -> iniciarDiagnostico(serializador.converter(conteudo, IniciarDiagnosticoJson.class));
                case "ReabrirDiagnostico" -> new DadosComando.ReabrirDiagnostico(motivo(conteudo));
                case "IniciarReparo" -> iniciarReparo(serializador.converter(conteudo, IniciarReparoJson.class));
                case "CancelarExecucao" -> new DadosComando.CancelarExecucao(motivo(conteudo));
                default -> new DadosComando.NaoReconhecido();
            };
        } catch (JsonProcessingException e) {
            return new DadosComando.Invalido("Dados inválidos para " + tipo + ": " + e.getOriginalMessage());
        } catch (DomainException | IllegalArgumentException e) {
            return new DadosComando.Invalido("Dados inválidos para " + tipo + ": " + e.getMessage());
        }
    }

    private String motivo(JsonNode conteudo) throws JsonProcessingException {
        return serializador.converter(conteudo, MotivoJson.class).motivo();
    }

    private static DadosComando iniciarDiagnostico(IniciarDiagnosticoJson json) {
        var veiculo = json.veiculo() == null ? null : new Veiculo(
            json.veiculo().placa(), json.veiculo().marca(), json.veiculo().modelo(), json.veiculo().anoFabricacao());
        return new DadosComando.IniciarDiagnostico(json.clienteId(), veiculo, json.abertaEm());
    }

    private static DadosComando iniciarReparo(IniciarReparoJson json) {
        if (json.itens() == null) {
            return new DadosComando.IniciarReparo(null);
        }
        var itens = json.itens().stream().map(LeitorComandos::item).toList();
        return new DadosComando.IniciarReparo(itens);
    }

    private static ItemReparo item(ItemJson json) {
        if (json == null) {
            throw new DomainException("Item de reparo ausente.");
        }
        var quantidade = json.quantidade() == null ? 0 : json.quantidade();
        return new ItemReparo(json.tipo(), json.descricao(), quantidade);
    }
}
