package com.oficina.execution.domain.entity;

import com.oficina.execution.domain.exception.DomainException;
import com.oficina.execution.domain.exception.OperacaoNaoPermitidaException;
import com.oficina.execution.domain.exception.TransicaoInvalidaException;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.oficina.execution.domain.valueobject.StatusExecucao.AGUARDANDO_DIAGNOSTICO;
import static com.oficina.execution.domain.valueobject.StatusExecucao.AGUARDANDO_REPARO;
import static com.oficina.execution.domain.valueobject.StatusExecucao.CANCELADA;
import static com.oficina.execution.domain.valueobject.StatusExecucao.DIAGNOSTICO_CONCLUIDO;
import static com.oficina.execution.domain.valueobject.StatusExecucao.EM_DIAGNOSTICO;
import static com.oficina.execution.domain.valueobject.StatusExecucao.EM_REPARO;
import static com.oficina.execution.domain.valueobject.StatusExecucao.FALHOU;
import static com.oficina.execution.domain.valueobject.StatusExecucao.FINALIZADA;
import static com.oficina.execution.domain.valueobject.TipoEtapa.DIAGNOSTICO;
import static com.oficina.execution.domain.valueobject.TipoEtapa.REPARO;

public class Execucao {

    private final UUID osId;
    private final UUID sagaId;
    private final UUID clienteId;
    private final Veiculo veiculo;
    private StatusExecucao status;
    private TipoEtapa etapaAtual;
    private int prioridade;
    private Instant enfileiradaEm;
    private List<ItemReparo> itensReparo;
    private final List<Etapa> etapas;
    private final List<Apontamento> apontamentos;
    private final List<HistoricoStatus> historicoStatus;
    private String observacoesDiagnostico;
    private String motivoFalha;
    private String motivoCancelamento;
    private Long versao;

    private final List<TransicaoExecucao> transicoesPendentes = new ArrayList<>();

    private Execucao(DadosExecucao dados) {
        this.osId = dados.osId();
        this.sagaId = dados.sagaId();
        this.clienteId = dados.clienteId();
        this.veiculo = dados.veiculo();
        this.status = dados.status();
        this.etapaAtual = dados.etapaAtual();
        this.prioridade = dados.prioridade();
        this.enfileiradaEm = dados.enfileiradaEm();
        this.itensReparo = copia(dados.itensReparo());
        this.etapas = copia(dados.etapas());
        this.apontamentos = copia(dados.apontamentos());
        this.historicoStatus = copia(dados.historicoStatus());
        this.observacoesDiagnostico = dados.observacoesDiagnostico();
        this.motivoFalha = dados.motivoFalha();
        this.motivoCancelamento = dados.motivoCancelamento();
        this.versao = dados.versao();
    }

    public static Execucao enfileirarDiagnostico(UUID osId, UUID sagaId, UUID clienteId, Veiculo veiculo, Instant agora) {
        exigir(osId != null, "A ordem de serviço é obrigatória.");
        exigir(clienteId != null, "O cliente é obrigatório.");
        exigir(veiculo != null, "O veículo é obrigatório.");
        var execucao = new Execucao(new DadosExecucao(
            osId, sagaId, clienteId, veiculo, AGUARDANDO_DIAGNOSTICO, DIAGNOSTICO, 0, agora,
            List.of(), List.of(), List.of(), List.of(), null, null, null, null));
        execucao.historicoStatus.add(new HistoricoStatus(null, AGUARDANDO_DIAGNOSTICO, agora, null));
        execucao.transicoesPendentes.add(new TransicaoExecucao(osId, null, AGUARDANDO_DIAGNOSTICO, DIAGNOSTICO, null));
        return execucao;
    }

    public static Execucao reconstituir(DadosExecucao dados) {
        return new Execucao(dados);
    }

    public void iniciarDiagnostico(UUID mecanicoId, Instant agora) {
        exigirOrigem(AGUARDANDO_DIAGNOSTICO, EM_DIAGNOSTICO);
        exigirMecanico(mecanicoId);
        transicionar(EM_DIAGNOSTICO, etapaAtual, agora, null, null);
        etapas.add(new Etapa(DIAGNOSTICO, mecanicoId, agora, null));
    }

    public void concluirDiagnostico(String observacoes, Instant agora) {
        exigir(temTexto(observacoes), "As observações do diagnóstico são obrigatórias.");
        transicionar(DIAGNOSTICO_CONCLUIDO, etapaAtual, agora, null, duracaoDaEtapaEmAndamento(agora));
        encerrarEtapaEmAndamento(agora);
        this.observacoesDiagnostico = observacoes;
    }

    public void reabrirDiagnostico(String motivo, Instant agora) {
        exigirOrigem(DIAGNOSTICO_CONCLUIDO, EM_DIAGNOSTICO);
        var mecanicoId = ultimaEtapa(DIAGNOSTICO).map(Etapa::mecanicoId).orElse(null);
        transicionar(EM_DIAGNOSTICO, etapaAtual, agora, motivo, null);
        etapas.add(new Etapa(DIAGNOSTICO, mecanicoId, agora, null));
        var texto = temTexto(motivo) ? "Diagnóstico reaberto: " + motivo : "Diagnóstico reaberto.";
        apontamentos.add(new Apontamento(null, texto, agora));
    }

    public void enfileirarReparo(List<ItemReparo> itens, Instant agora) {
        exigir(itens != null && !itens.isEmpty(), "O reparo exige ao menos um item.");
        transicionar(AGUARDANDO_REPARO, REPARO, agora, null, null);
        this.enfileiradaEm = agora;
        this.itensReparo = new ArrayList<>(itens);
    }

    public void iniciarReparo(UUID mecanicoId, Instant agora) {
        exigirMecanico(mecanicoId);
        transicionar(EM_REPARO, etapaAtual, agora, null, null);
        etapas.add(new Etapa(REPARO, mecanicoId, agora, null));
    }

    public void concluirReparo(Instant agora) {
        transicionar(FINALIZADA, etapaAtual, agora, null, duracaoDaEtapaEmAndamento(agora));
        encerrarEtapaEmAndamento(agora);
    }

    public void registrarFalha(String motivo, Instant agora) {
        exigir(temTexto(motivo), "O motivo da falha é obrigatório.");
        transicionar(FALHOU, etapaAtual, agora, motivo, null);
        encerrarEtapaEmAndamento(agora);
        this.motivoFalha = motivo;
    }

    public void cancelar(String motivo, Instant agora) {
        transicionar(CANCELADA, etapaAtual, agora, motivo, null);
        encerrarEtapaEmAndamento(agora);
        this.motivoCancelamento = motivo;
    }

    public void registrarApontamento(UUID mecanicoId, String texto, Instant agora) {
        exigirNaoTerminal("registrar apontamento");
        exigirMecanico(mecanicoId);
        exigir(temTexto(texto), "O texto do apontamento é obrigatório.");
        apontamentos.add(new Apontamento(mecanicoId, texto, agora));
    }

    public void alterarPrioridade(int novaPrioridade) {
        exigirNaoTerminal("alterar prioridade");
        this.prioridade = novaPrioridade;
    }

    public Optional<Instant> instanteDoStatus(StatusExecucao alvo) {
        return historicoStatus.reversed().stream()
            .filter(h -> h.para() == alvo)
            .map(HistoricoStatus::em)
            .findFirst();
    }

    public Optional<Instant> enfileiramentoDa(TipoEtapa etapa) {
        return instanteDoStatus(etapa == DIAGNOSTICO ? AGUARDANDO_DIAGNOSTICO : AGUARDANDO_REPARO);
    }

    public Optional<Etapa> ultimaEtapa(TipoEtapa tipo) {
        return etapas.reversed().stream().filter(e -> e.tipo() == tipo).findFirst();
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public List<TransicaoExecucao> drenarTransicoes() {
        var copia = List.copyOf(transicoesPendentes);
        transicoesPendentes.clear();
        return copia;
    }

    public void sincronizarVersao(Long versaoPersistida) {
        this.versao = versaoPersistida;
    }

    private void transicionar(StatusExecucao destino, TipoEtapa etapaDestino, Instant agora,
                              String motivo, Duration duracaoEtapa) {
        status.validarTransicaoPara(destino);
        var origem = status;
        this.status = destino;
        this.etapaAtual = etapaDestino;
        historicoStatus.add(new HistoricoStatus(origem, destino, agora, motivo));
        transicoesPendentes.add(new TransicaoExecucao(osId, origem, destino, etapaDestino, duracaoEtapa));
    }

    private Duration duracaoDaEtapaEmAndamento(Instant agora) {
        return etapaEmAndamento().map(e -> Duration.between(e.inicio(), agora)).orElse(null);
    }

    private void encerrarEtapaEmAndamento(Instant agora) {
        for (int i = etapas.size() - 1; i >= 0; i--) {
            if (etapas.get(i).emAndamento()) {
                etapas.set(i, etapas.get(i).encerrar(agora));
                return;
            }
        }
    }

    private Optional<Etapa> etapaEmAndamento() {
        return etapas.reversed().stream().filter(Etapa::emAndamento).findFirst();
    }

    private void exigirOrigem(StatusExecucao esperado, StatusExecucao destino) {
        if (status != esperado) {
            throw new TransicaoInvalidaException("Transição inválida: %s → %s".formatted(status, destino));
        }
    }

    private void exigirNaoTerminal(String operacao) {
        if (isTerminal()) {
            throw new OperacaoNaoPermitidaException(
                "Operação '%s' não permitida no status %s.".formatted(operacao, status));
        }
    }

    private static void exigirMecanico(UUID mecanicoId) {
        exigir(mecanicoId != null, "O mecânico é obrigatório.");
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new DomainException(mensagem);
        }
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static <T> List<T> copia(List<T> origem) {
        return origem == null ? new ArrayList<>() : new ArrayList<>(origem);
    }

    public UUID getOsId() { return osId; }
    public UUID getSagaId() { return sagaId; }
    public UUID getClienteId() { return clienteId; }
    public Veiculo getVeiculo() { return veiculo; }
    public StatusExecucao getStatus() { return status; }
    public TipoEtapa getEtapaAtual() { return etapaAtual; }
    public int getPrioridade() { return prioridade; }
    public Instant getEnfileiradaEm() { return enfileiradaEm; }
    public List<ItemReparo> getItensReparo() { return Collections.unmodifiableList(itensReparo); }
    public List<Etapa> getEtapas() { return Collections.unmodifiableList(etapas); }
    public List<Apontamento> getApontamentos() { return Collections.unmodifiableList(apontamentos); }
    public List<HistoricoStatus> getHistoricoStatus() { return Collections.unmodifiableList(historicoStatus); }
    public String getObservacoesDiagnostico() { return observacoesDiagnostico; }
    public String getMotivoFalha() { return motivoFalha; }
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public Long getVersao() { return versao; }
}
