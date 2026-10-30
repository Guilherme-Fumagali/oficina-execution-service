package com.oficina.execution.integracao;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oficina.execution.domain.entity.Execucao;
import com.oficina.execution.domain.repository.ExecucaoRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.oficina.execution.ExecucaoFixture.MECANICO;
import static com.oficina.execution.ExecucaoFixture.itens;
import static com.oficina.execution.ExecucaoFixture.veiculo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExecucaoApiIntegracaoTest extends IntegracaoBase {

    @Value("${jwt.secret}") String jwtSecret;

    @Autowired MockMvc mvc;
    @Autowired ExecucaoRepository repository;

    @Test
    void fluxoCompletoDoDiagnosticoAoReparoPublicaOsEventosDaSaga() throws Exception {
        var osId = UUID.randomUUID();
        var sagaId = UUID.randomUUID();
        var mecanico = UUID.randomUUID();
        var token = token(mecanico, "FUNCIONARIO");

        enviarComando("IniciarDiagnostico", osId, sagaId, dadosIniciarDiagnostico(UUID.randomUUID()));
        aguardarEvento(osId, "ExecucaoEnfileirada");

        mvc.perform(post("/api/execucoes/{osId}/diagnostico/inicio", osId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"))
            .andExpect(jsonPath("$.etapas[0].mecanicoId").value(mecanico.toString()));
        var iniciado = aguardarEvento(osId, "DiagnosticoIniciado");
        assertThat(iniciado.dados().get("mecanicoId").asText()).isEqualTo(mecanico.toString());
        assertThat(iniciado.causadaPor()).isNull();

        mvc.perform(post("/api/execucoes/{osId}/apontamentos", osId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Folga na caixa de direção\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.apontamentos", hasSize(1)));

        mvc.perform(post("/api/execucoes/{osId}/diagnostico/conclusao", osId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"observacoes\":\"Trocar terminal de direção\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DIAGNOSTICO_CONCLUIDO"))
            .andExpect(jsonPath("$.observacoesDiagnostico").value("Trocar terminal de direção"));
        var concluido = aguardarEvento(osId, "DiagnosticoConcluido");
        assertThat(concluido.dados().get("observacoes").asText()).isEqualTo("Trocar terminal de direção");

        enviarComando("IniciarReparo", osId, sagaId, Map.of("itens", List.of(
            Map.of("tipo", "SERVICO", "descricao", "Troca de terminal", "quantidade", 1),
            Map.of("tipo", "PECA", "descricao", "Terminal de direção", "quantidade", 2))));
        aguardarEventos(osId, "ExecucaoEnfileirada", 2);

        mvc.perform(post("/api/execucoes/{osId}/reparo/inicio", osId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EM_REPARO"))
            .andExpect(jsonPath("$.itensReparo", hasSize(2)));
        aguardarEvento(osId, "ReparoIniciado");

        mvc.perform(post("/api/execucoes/{osId}/reparo/conclusao", osId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FINALIZADA"));
        var finalizada = aguardarEvento(osId, "ExecucaoFinalizada");
        assertThat(finalizada.dados().get("mecanicoId").asText()).isEqualTo(mecanico.toString());

        assertThat(eventos(osId)).extracting(EventoRecebido::tipo).containsExactly(
            "ExecucaoEnfileirada", "DiagnosticoIniciado", "DiagnosticoConcluido",
            "ExecucaoEnfileirada", "ReparoIniciado", "ExecucaoFinalizada");
        assertThat(eventos(osId)).allSatisfy(e -> assertThat(e.envelope().get("sagaId").asText())
            .isEqualTo(sagaId.toString()));
        assertThat(eventos(osId, "ExecucaoEnfileirada").getLast().dados().get("etapa").asText()).isEqualTo("REPARO");
    }

    @Test
    void falhaNoReparoPublicaExecucaoFalhou() throws Exception {
        var execucao = salvarEmFilaDeReparo();

        mvc.perform(post("/api/execucoes/{osId}/falha", execucao.getOsId()).header("Authorization", "Bearer " + funcionario())
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Peça entregue com defeito\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FALHOU"))
            .andExpect(jsonPath("$.motivoFalha").value("Peça entregue com defeito"));

        var falhou = aguardarEvento(execucao.getOsId(), "ExecucaoFalhou");
        assertThat(falhou.dados().get("motivo").asText()).isEqualTo("Peça entregue com defeito");
    }

    @Test
    void transicaoInvalidaRetorna422SemPublicarEvento() throws Exception {
        var execucao = salvarNaFilaDeDiagnostico(Instant.now(), 0);

        mvc.perform(post("/api/execucoes/{osId}/reparo/conclusao", execucao.getOsId())
                .header("Authorization", "Bearer " + funcionario()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Transição inválida: AGUARDANDO_DIAGNOSTICO → FINALIZADA"));

        mvc.perform(post("/api/execucoes/{osId}/falha", execucao.getOsId()).header("Authorization", "Bearer " + funcionario())
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
            .andExpect(status().isUnprocessableEntity());
        coletarEventos();
        assertThat(eventos(execucao.getOsId())).isEmpty();
    }

    @Test
    void operacaoEmExecucaoEncerradaRetorna422() throws Exception {
        var execucao = salvarNaFilaDeDiagnostico(Instant.now(), 0);
        execucao.cancelar("Orçamento reprovado", Instant.now());
        repository.salvar(execucao);

        mvc.perform(post("/api/execucoes/{osId}/apontamentos", execucao.getOsId())
                .header("Authorization", "Bearer " + funcionario())
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"tarde demais\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail", containsString("CANCELADA")));
    }

    @Test
    void execucaoInexistenteRetorna404() throws Exception {
        mvc.perform(get("/api/execucoes/{osId}", UUID.randomUUID()).header("Authorization", "Bearer " + funcionario()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
        mvc.perform(post("/api/execucoes/{osId}/diagnostico/inicio", UUID.randomUUID())
                .header("Authorization", "Bearer " + funcionario()))
            .andExpect(status().isNotFound());
    }

    @Test
    void requisicoesMalformadasRetornam400() throws Exception {
        var execucao = salvarNaFilaDeDiagnostico(Instant.now(), 0);
        var token = funcionario();

        mvc.perform(post("/api/execucoes/{osId}/diagnostico/conclusao", execucao.getOsId())
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"observacoes\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", containsString("observacoes")));
        mvc.perform(patch("/api/execucoes/{osId}/prioridade", execucao.getOsId())
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"prioridade\":101}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/execucoes/{osId}/prioridade", execucao.getOsId())
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{prioridade"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Corpo da requisição ausente ou malformado."));
        mvc.perform(get("/api/execucoes/nao-e-uuid").header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Valor inválido para o parâmetro osId."));
        mvc.perform(get("/api/execucoes").param("status", "INEXISTENTE").header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    @Test
    void autenticacaoEPapeisSaoExigidos() throws Exception {
        var execucao = salvarNaFilaDeDiagnostico(Instant.now(), 0);
        var cliente = token(execucao.getClienteId(), "CLIENTE");

        mvc.perform(get("/api/execucoes"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Autenticação necessária."));
        mvc.perform(get("/api/execucoes").header("Authorization", "Bearer token-invalido"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/execucoes").header("Authorization", "Bearer " + cliente))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/execucoes/{osId}/diagnostico/inicio", execucao.getOsId())
                .header("Authorization", "Bearer " + cliente))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/execucoes/{osId}", execucao.getOsId())
                .header("Authorization", "Bearer " + token(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteVeSomenteAPropriaExecucaoEmVisaoReduzida() throws Exception {
        var execucao = salvarEmFilaDeReparo();
        var tokenDono = token(execucao.getClienteId(), "CLIENTE");

        var resposta = mvc.perform(get("/api/execucoes/{osId}", execucao.getOsId()).header("Authorization", "Bearer " + tokenDono))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("AGUARDANDO_REPARO"))
            .andExpect(jsonPath("$.etapas", hasSize(1)))
            .andExpect(jsonPath("$.itensReparo", hasSize(2)))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var json = JSON.readTree(resposta);
        assertThat(json.has("apontamentos")).isFalse();
        assertThat(json.has("sagaId")).isFalse();
        assertThat(json.has("prioridade")).isFalse();
        assertThat(resposta).doesNotContain("mecanicoId").doesNotContain(MECANICO.toString());

        mvc.perform(get("/api/execucoes/{osId}", execucao.getOsId())
                .header("Authorization", "Bearer " + token(UUID.randomUUID(), "CLIENTE")))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/execucoes/{osId}", execucao.getOsId()).header("Authorization", "Bearer " + funcionario()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apontamentos", hasSize(1)))
            .andExpect(jsonPath("$.etapas[0].mecanicoId").value(MECANICO.toString()))
            .andExpect(jsonPath("$.sagaId").value(execucao.getSagaId().toString()))
            .andExpect(jsonPath("$.versao").value(1));
    }

    @Test
    void filaOrdenadaPorPrioridadeDecrescenteEDepoisPorEntrada() throws Exception {
        var base = Instant.parse("2021-06-01T10:00:00Z");
        var primeiraSemPrioridade = salvarNaFilaDeDiagnostico(base, 0);
        var segundaSemPrioridade = salvarNaFilaDeDiagnostico(base.plusSeconds(10), 0);
        var urgenteRecente = salvarNaFilaDeDiagnostico(base.plusSeconds(20), 0);
        var urgenteAntiga = salvarNaFilaDeDiagnostico(base.plusSeconds(5), 0);
        var cancelada = salvarNaFilaDeDiagnostico(base.plusSeconds(1), 0);
        cancelada.cancelar("Reprovado", Instant.now());
        repository.salvar(cancelada);
        var token = funcionario();
        for (var urgente : List.of(urgenteRecente, urgenteAntiga)) {
            mvc.perform(patch("/api/execucoes/{osId}/prioridade", urgente.getOsId())
                    .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"prioridade\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prioridade").value(7));
        }
        var esperadas = List.of(urgenteAntiga, urgenteRecente, primeiraSemPrioridade, segundaSemPrioridade).stream()
            .map(e -> e.getOsId().toString()).toList();

        var filtrada = idsNaOrdem(mvc.perform(get("/api/execucoes").param("status", "AGUARDANDO_DIAGNOSTICO")
                .param("etapa", "DIAGNOSTICO").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        var semFiltro = idsNaOrdem(mvc.perform(get("/api/execucoes").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(filtrada.stream().filter(esperadas::contains).toList()).containsExactlyElementsOf(esperadas);
        assertThat(semFiltro.stream().filter(esperadas::contains).toList()).containsExactlyElementsOf(esperadas);
        assertThat(semFiltro).doesNotContain(cancelada.getOsId().toString());
    }

    @Test
    void healthEInfoSaoPublicos() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @Test
    void contratoOpenApiVersionadoDeveCorresponderAAplicacao() throws Exception {
        var resposta = mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        var ordenado = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
        var contrato = (ObjectNode) ordenado.readTree(resposta);
        contrato.remove("servers");
        var gerado = ordenado.writeValueAsString(ordenado.convertValue(contrato, Object.class)) + "\n";

        var arquivo = Path.of("docs/openapi.json");
        if (Boolean.getBoolean("atualizarOpenApi")) {
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, gerado, StandardCharsets.UTF_8);
        }

        assertThat(arquivo)
            .as("docs/openapi.json desatualizado: execute ./mvnw test -Dtest=ExecucaoApiIntegracaoTest -DatualizarOpenApi=true")
            .exists()
            .content(StandardCharsets.UTF_8).isEqualTo(gerado);
    }

    private Execucao salvarNaFilaDeDiagnostico(Instant enfileiradaEm, int prioridade) {
        var execucao = Execucao.enfileirarDiagnostico(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            veiculo(), enfileiradaEm);
        execucao.alterarPrioridade(prioridade);
        return repository.salvar(execucao);
    }

    private Execucao salvarEmFilaDeReparo() {
        var execucao = salvarNaFilaDeDiagnostico(Instant.now().minusSeconds(3600), 0);
        execucao.iniciarDiagnostico(MECANICO, Instant.now().minusSeconds(3000));
        execucao.registrarApontamento(MECANICO, "Óleo contaminado", Instant.now().minusSeconds(2800));
        execucao.concluirDiagnostico("Troca de óleo e filtro", Instant.now().minusSeconds(2500));
        execucao.enfileirarReparo(itens(), Instant.now().minusSeconds(60));
        return repository.salvar(execucao);
    }

    private List<String> idsNaOrdem(String json) throws Exception {
        var ids = new ArrayList<String>();
        JSON.readTree(json).forEach(no -> ids.add(no.get("osId").asText()));
        return ids;
    }

    private String funcionario() {
        return token(UUID.randomUUID(), "FUNCIONARIO");
    }

    private String token(UUID sub, String role) {
        var agora = Instant.now();
        var chave = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .subject(sub.toString())
            .issuer("oficina-auth")
            .claim("nome", "Usuário de Teste")
            .claim("role", role)
            .issuedAt(Date.from(agora))
            .expiration(Date.from(agora.plusSeconds(900)))
            .signWith(chave)
            .compact();
    }
}
