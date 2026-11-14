package com.oficina.execution.infrastructure.persistence;

import com.oficina.execution.domain.entity.Apontamento;
import com.oficina.execution.domain.entity.DadosExecucao;
import com.oficina.execution.domain.entity.Etapa;
import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.entity.HistoricoStatus;
import com.oficina.execution.domain.entity.ItemReparo;
import com.oficina.execution.domain.entity.Veiculo;
import com.oficina.execution.infrastructure.persistence.document.ExecucaoDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

final class ExecucaoDocumentMapper {

    private ExecucaoDocumentMapper() {}

    static ExecucaoDocument paraDocumento(Execucao execucao) {
        var documento = new ExecucaoDocument();
        documento.setOsId(texto(execucao.getOsId()));
        documento.setSagaId(texto(execucao.getSagaId()));
        documento.setClienteId(texto(execucao.getClienteId()));
        var veiculo = execucao.getVeiculo();
        documento.setVeiculo(new ExecucaoDocument.VeiculoDocument(
            veiculo.placa(), veiculo.marca(), veiculo.modelo(), veiculo.anoFabricacao()));
        documento.setStatus(execucao.getStatus());
        documento.setEtapaAtual(execucao.getEtapaAtual());
        documento.setPrioridade(execucao.getPrioridade());
        documento.setEnfileiradaEm(execucao.getEnfileiradaEm());
        documento.setItensReparo(mapear(execucao.getItensReparo(),
            i -> new ExecucaoDocument.ItemReparoDocument(i.tipo(), i.descricao(), i.quantidade())));
        documento.setEtapas(mapear(execucao.getEtapas(),
            e -> new ExecucaoDocument.EtapaDocument(e.tipo(), texto(e.mecanicoId()), e.inicio(), e.fim())));
        documento.setApontamentos(mapear(execucao.getApontamentos(),
            a -> new ExecucaoDocument.ApontamentoDocument(texto(a.mecanicoId()), a.texto(), a.registradoEm())));
        documento.setHistoricoStatus(mapear(execucao.getHistoricoStatus(),
            h -> new ExecucaoDocument.HistoricoStatusDocument(h.de(), h.para(), h.em(), h.motivo())));
        documento.setObservacoesDiagnostico(execucao.getObservacoesDiagnostico());
        documento.setMotivoFalha(execucao.getMotivoFalha());
        documento.setMotivoCancelamento(execucao.getMotivoCancelamento());
        documento.setVersao(execucao.getVersao());
        return documento;
    }

    static Execucao paraDominio(ExecucaoDocument documento) {
        var veiculo = documento.getVeiculo();
        return Execucao.reconstituir(new DadosExecucao(
            uuid(documento.getOsId()),
            uuid(documento.getSagaId()),
            uuid(documento.getClienteId()),
            new Veiculo(veiculo.placa(), veiculo.marca(), veiculo.modelo(), veiculo.anoFabricacao()),
            documento.getStatus(),
            documento.getEtapaAtual(),
            documento.getPrioridade(),
            documento.getEnfileiradaEm(),
            mapear(documento.getItensReparo(), i -> new ItemReparo(i.tipo(), i.descricao(), i.quantidade())),
            mapear(documento.getEtapas(), e -> new Etapa(e.tipo(), uuid(e.mecanicoId()), e.inicio(), e.fim())),
            mapear(documento.getApontamentos(), a -> new Apontamento(uuid(a.mecanicoId()), a.texto(), a.registradoEm())),
            mapear(documento.getHistoricoStatus(), h -> new HistoricoStatus(h.de(), h.para(), h.em(), h.motivo())),
            documento.getObservacoesDiagnostico(),
            documento.getMotivoFalha(),
            documento.getMotivoCancelamento(),
            documento.getVersao()));
    }

    private static <O, D> List<D> mapear(List<O> origem, Function<O, D> conversao) {
        if (origem == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(origem.stream().map(conversao).toList());
    }

    private static String texto(UUID id) {
        return id == null ? null : id.toString();
    }

    private static UUID uuid(String valor) {
        return valor == null ? null : UUID.fromString(valor);
    }
}
