package com.oficina.execution.infrastructure.web;

import com.mongodb.MongoException;
import com.oficina.execution.domain.exception.DomainException;
import com.oficina.execution.infrastructure.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void conflitoDeVersaoRetorna409() {
        var resposta = handler.handleAcessoADados(new OptimisticLockingFailureException("versão divergente"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().getDetail()).contains("alterada por outra operação");
    }

    @Test
    void violacaoDeIntegridadeRetorna409() {
        assertThat(handler.handleAcessoADados(new DataIntegrityViolationException("duplicada")).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void conflitoDeEscritaEmTransacaoRetorna409() {
        var porCodigo = new UncategorizedMongoDbException("WriteConflict", new MongoException(112, "WriteConflict"));
        var rotulada = new MongoException(251, "NoSuchTransaction");
        rotulada.addLabel("TransientTransactionError");

        assertThat(handler.handleAcessoADados(porCodigo).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleAcessoADados(new UncategorizedMongoDbException("erro", rotulada)).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void bancoIndisponivelRetorna503() {
        var resposta = handler.handleAcessoADados(new DataAccessResourceFailureException("sem conexão",
            new MongoException(6, "HostUnreachable")));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void erroInesperadoRetorna500SemDetalhesInternos() {
        var resposta = handler.handleInesperado(new IllegalStateException("NullPointer em lugar sensível"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().getDetail()).isEqualTo("Erro interno ao processar a requisição.");
    }

    @Test
    void regraDeDominioRetorna400() {
        assertThat(handler.handleDomain(new DomainException("O motivo da falha é obrigatório.")).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
