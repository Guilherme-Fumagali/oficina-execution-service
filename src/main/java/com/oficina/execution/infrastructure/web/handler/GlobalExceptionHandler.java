package com.oficina.execution.infrastructure.web.handler;

import com.mongodb.MongoException;
import com.oficina.execution.domain.exception.DomainException;
import com.oficina.execution.domain.exception.OperacaoNaoPermitidaException;
import com.oficina.execution.domain.exception.RecursoNaoEncontradoException;
import com.oficina.execution.domain.exception.TransicaoInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String CONFLITO = "A execução foi alterada por outra operação. Consulte novamente e repita a ação.";
    private static final int CONFLITO_DE_ESCRITA = 112;
    private static final String ERRO_TRANSITORIO = "TransientTransactionError";

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ProblemDetail> handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({TransicaoInvalidaException.class, OperacaoNaoPermitidaException.class})
    public ResponseEntity<ProblemDetail> handleTransicao(DomainException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(DomainException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleAcessoADados(DataAccessException ex) {
        if (ex instanceof OptimisticLockingFailureException || ex instanceof DataIntegrityViolationException
                || conflitoDeTransacao(ex)) {
            return build(HttpStatus.CONFLICT, CONFLITO);
        }
        log.error("Falha de acesso ao banco", ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Banco de dados indisponível. Tente novamente.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleInesperado(Exception ex) {
        log.error("Erro não tratado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao processar a requisição.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        var erros = ex.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erros));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "Corpo da requisição ausente ou malformado."));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
                                                        HttpHeaders headers, HttpStatusCode status,
                                                        WebRequest request) {
        var parametro = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "Valor inválido para o parâmetro " + parametro + "."));
    }

    private static boolean conflitoDeTransacao(Throwable erro) {
        for (var causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof MongoException mongo
                    && (mongo.getCode() == CONFLITO_DE_ESCRITA || mongo.hasErrorLabel(ERRO_TRANSITORIO))) {
                return true;
            }
        }
        return false;
    }

    private static ResponseEntity<ProblemDetail> build(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
