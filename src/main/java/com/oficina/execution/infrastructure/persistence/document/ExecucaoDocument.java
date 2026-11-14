package com.oficina.execution.infrastructure.persistence.document;

import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;
import com.oficina.execution.domain.valueobject.TipoItemReparo;
import com.oficina.execution.infrastructure.persistence.ColecoesMongo;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = ColecoesMongo.EXECUCOES)
public class ExecucaoDocument {

    @Id
    private String osId;
    private String sagaId;
    private String clienteId;
    private VeiculoDocument veiculo;
    private StatusExecucao status;
    private TipoEtapa etapaAtual;
    private int prioridade;
    private Instant enfileiradaEm;
    private List<ItemReparoDocument> itensReparo = new ArrayList<>();
    private List<EtapaDocument> etapas = new ArrayList<>();
    private List<ApontamentoDocument> apontamentos = new ArrayList<>();
    private List<HistoricoStatusDocument> historicoStatus = new ArrayList<>();
    private String observacoesDiagnostico;
    private String motivoFalha;
    private String motivoCancelamento;
    @Version
    private Long versao;

    public record VeiculoDocument(String placa, String marca, String modelo, Integer anoFabricacao) {}

    public record ItemReparoDocument(TipoItemReparo tipo, String descricao, int quantidade) {}

    public record EtapaDocument(TipoEtapa tipo, String mecanicoId, Instant inicio, Instant fim) {}

    public record ApontamentoDocument(String mecanicoId, String texto, Instant registradoEm) {}

    public record HistoricoStatusDocument(StatusExecucao de, StatusExecucao para, Instant em, String motivo) {}

    public String getOsId() { return osId; }
    public void setOsId(String osId) { this.osId = osId; }
    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }
    public VeiculoDocument getVeiculo() { return veiculo; }
    public void setVeiculo(VeiculoDocument veiculo) { this.veiculo = veiculo; }
    public StatusExecucao getStatus() { return status; }
    public void setStatus(StatusExecucao status) { this.status = status; }
    public TipoEtapa getEtapaAtual() { return etapaAtual; }
    public void setEtapaAtual(TipoEtapa etapaAtual) { this.etapaAtual = etapaAtual; }
    public int getPrioridade() { return prioridade; }
    public void setPrioridade(int prioridade) { this.prioridade = prioridade; }
    public Instant getEnfileiradaEm() { return enfileiradaEm; }
    public void setEnfileiradaEm(Instant enfileiradaEm) { this.enfileiradaEm = enfileiradaEm; }
    public List<ItemReparoDocument> getItensReparo() { return itensReparo; }
    public void setItensReparo(List<ItemReparoDocument> itensReparo) { this.itensReparo = itensReparo; }
    public List<EtapaDocument> getEtapas() { return etapas; }
    public void setEtapas(List<EtapaDocument> etapas) { this.etapas = etapas; }
    public List<ApontamentoDocument> getApontamentos() { return apontamentos; }
    public void setApontamentos(List<ApontamentoDocument> apontamentos) { this.apontamentos = apontamentos; }
    public List<HistoricoStatusDocument> getHistoricoStatus() { return historicoStatus; }
    public void setHistoricoStatus(List<HistoricoStatusDocument> historicoStatus) { this.historicoStatus = historicoStatus; }
    public String getObservacoesDiagnostico() { return observacoesDiagnostico; }
    public void setObservacoesDiagnostico(String observacoesDiagnostico) { this.observacoesDiagnostico = observacoesDiagnostico; }
    public String getMotivoFalha() { return motivoFalha; }
    public void setMotivoFalha(String motivoFalha) { this.motivoFalha = motivoFalha; }
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public void setMotivoCancelamento(String motivoCancelamento) { this.motivoCancelamento = motivoCancelamento; }
    public Long getVersao() { return versao; }
    public void setVersao(Long versao) { this.versao = versao; }
}
