package com.oficina.execution.infrastructure.web.controller;

import com.oficina.execution.application.usecase.execucao.AlterarPrioridadeUseCase;
import com.oficina.execution.application.usecase.execucao.ConcluirDiagnosticoUseCase;
import com.oficina.execution.application.usecase.execucao.ConcluirReparoUseCase;
import com.oficina.execution.application.usecase.execucao.ConsultarExecucaoUseCase;
import com.oficina.execution.application.usecase.execucao.ConsultarFilaUseCase;
import com.oficina.execution.application.usecase.execucao.IniciarDiagnosticoUseCase;
import com.oficina.execution.application.usecase.execucao.IniciarReparoUseCase;
import com.oficina.execution.application.usecase.execucao.RegistrarApontamentoUseCase;
import com.oficina.execution.application.usecase.execucao.RegistrarFalhaUseCase;
import com.oficina.execution.domain.valueobject.StatusExecucao;
import com.oficina.execution.domain.valueobject.TipoEtapa;
import com.oficina.execution.infrastructure.security.Solicitante;
import com.oficina.execution.infrastructure.web.dto.request.AlterarPrioridadeRequest;
import com.oficina.execution.infrastructure.web.dto.request.ConcluirDiagnosticoRequest;
import com.oficina.execution.infrastructure.web.dto.request.RegistrarApontamentoRequest;
import com.oficina.execution.infrastructure.web.dto.request.RegistrarFalhaRequest;
import com.oficina.execution.infrastructure.web.dto.response.ExecucaoClienteResponse;
import com.oficina.execution.infrastructure.web.dto.response.ExecucaoResponse;
import com.oficina.execution.infrastructure.web.dto.response.ExecucaoResumoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/execucoes")
@Tag(name = "Execuções")
@SecurityRequirement(name = "bearerAuth")
public class ExecucaoController {

    private final ConsultarFilaUseCase consultarFila;
    private final ConsultarExecucaoUseCase consultarExecucao;
    private final IniciarDiagnosticoUseCase iniciarDiagnostico;
    private final ConcluirDiagnosticoUseCase concluirDiagnostico;
    private final IniciarReparoUseCase iniciarReparo;
    private final ConcluirReparoUseCase concluirReparo;
    private final RegistrarFalhaUseCase registrarFalha;
    private final RegistrarApontamentoUseCase registrarApontamento;
    private final AlterarPrioridadeUseCase alterarPrioridade;

    public ExecucaoController(ConsultarFilaUseCase consultarFila,
                              ConsultarExecucaoUseCase consultarExecucao,
                              IniciarDiagnosticoUseCase iniciarDiagnostico,
                              ConcluirDiagnosticoUseCase concluirDiagnostico,
                              IniciarReparoUseCase iniciarReparo,
                              ConcluirReparoUseCase concluirReparo,
                              RegistrarFalhaUseCase registrarFalha,
                              RegistrarApontamentoUseCase registrarApontamento,
                              AlterarPrioridadeUseCase alterarPrioridade) {
        this.consultarFila = consultarFila;
        this.consultarExecucao = consultarExecucao;
        this.iniciarDiagnostico = iniciarDiagnostico;
        this.concluirDiagnostico = concluirDiagnostico;
        this.iniciarReparo = iniciarReparo;
        this.concluirReparo = concluirReparo;
        this.registrarFalha = registrarFalha;
        this.registrarApontamento = registrarApontamento;
        this.alterarPrioridade = alterarPrioridade;
    }

    @GetMapping
    @Operation(summary = "Consultar a fila de execução, por prioridade decrescente e depois por ordem de entrada")
    public ResponseEntity<List<ExecucaoResumoResponse>> listar(@RequestParam(required = false) StatusExecucao status,
                                                               @RequestParam(required = false) TipoEtapa etapa) {
        return ResponseEntity.ok(consultarFila.executar(status, etapa).stream()
            .map(ExecucaoResumoResponse::from).toList());
    }

    @GetMapping("/{osId}")
    @Operation(summary = "Consultar a execução de uma OS (cliente: apenas a própria, sem apontamentos e sem mecânico)")
    @ApiResponse(responseCode = "200", description = "Execução encontrada", content = @Content(
        schema = @Schema(oneOf = {ExecucaoResponse.class, ExecucaoClienteResponse.class})))
    public ResponseEntity<Object> buscar(@PathVariable UUID osId) {
        var solicitante = Solicitante.atual();
        if (solicitante.funcionario()) {
            return ResponseEntity.ok(ExecucaoResponse.from(consultarExecucao.executar(osId)));
        }
        return ResponseEntity.ok(ExecucaoClienteResponse.from(consultarExecucao.executarDoCliente(osId, solicitante.id())));
    }

    @PostMapping("/{osId}/diagnostico/inicio")
    @Operation(summary = "Iniciar o diagnóstico (mecânico do token)")
    public ResponseEntity<ExecucaoResponse> iniciarDiagnostico(@PathVariable UUID osId) {
        return ResponseEntity.ok(ExecucaoResponse.from(iniciarDiagnostico.executar(osId, mecanico())));
    }

    @PostMapping("/{osId}/diagnostico/conclusao")
    @Operation(summary = "Concluir o diagnóstico com as observações")
    public ResponseEntity<ExecucaoResponse> concluirDiagnostico(@PathVariable UUID osId,
                                                                @Valid @RequestBody ConcluirDiagnosticoRequest req) {
        return ResponseEntity.ok(ExecucaoResponse.from(
            concluirDiagnostico.executar(osId, mecanico(), req.observacoes())));
    }

    @PostMapping("/{osId}/reparo/inicio")
    @Operation(summary = "Iniciar o reparo (mecânico do token)")
    public ResponseEntity<ExecucaoResponse> iniciarReparo(@PathVariable UUID osId) {
        return ResponseEntity.ok(ExecucaoResponse.from(iniciarReparo.executar(osId, mecanico())));
    }

    @PostMapping("/{osId}/reparo/conclusao")
    @Operation(summary = "Concluir o reparo e finalizar a execução")
    public ResponseEntity<ExecucaoResponse> concluirReparo(@PathVariable UUID osId) {
        return ResponseEntity.ok(ExecucaoResponse.from(concluirReparo.executar(osId, mecanico())));
    }

    @PostMapping("/{osId}/falha")
    @Operation(summary = "Registrar falha na execução do reparo (motivo obrigatório)")
    public ResponseEntity<ExecucaoResponse> registrarFalha(@PathVariable UUID osId,
                                                           @Valid @RequestBody RegistrarFalhaRequest req) {
        return ResponseEntity.ok(ExecucaoResponse.from(registrarFalha.executar(osId, mecanico(), req.motivo())));
    }

    @PostMapping("/{osId}/apontamentos")
    @Operation(summary = "Registrar apontamento do mecânico")
    public ResponseEntity<ExecucaoResponse> registrarApontamento(@PathVariable UUID osId,
                                                                 @Valid @RequestBody RegistrarApontamentoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ExecucaoResponse.from(
            registrarApontamento.executar(osId, mecanico(), req.texto())));
    }

    @PatchMapping("/{osId}/prioridade")
    @Operation(summary = "Alterar a prioridade na fila (maior valor é atendido antes)")
    public ResponseEntity<ExecucaoResponse> alterarPrioridade(@PathVariable UUID osId,
                                                              @Valid @RequestBody AlterarPrioridadeRequest req) {
        return ResponseEntity.ok(ExecucaoResponse.from(alterarPrioridade.executar(osId, req.prioridade())));
    }

    private static UUID mecanico() {
        return Solicitante.atual().id();
    }
}
