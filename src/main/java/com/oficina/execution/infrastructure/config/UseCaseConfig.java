package com.oficina.execution.infrastructure.config;

import com.oficina.execution.application.comando.RespostaComandos;
import com.oficina.execution.application.port.PublicadorEventos;
import com.oficina.execution.application.port.RegistroMensagensProcessadas;
import com.oficina.execution.application.port.UnidadeDeTrabalho;
import com.oficina.execution.application.usecase.comando.CancelarExecucaoUseCase;
import com.oficina.execution.application.usecase.comando.EnfileirarDiagnosticoUseCase;
import com.oficina.execution.application.usecase.comando.EnfileirarReparoUseCase;
import com.oficina.execution.application.usecase.comando.ProcessarComandoUseCase;
import com.oficina.execution.application.usecase.comando.ReabrirDiagnosticoUseCase;
import com.oficina.execution.application.usecase.execucao.AlterarPrioridadeUseCase;
import com.oficina.execution.application.usecase.execucao.ConcluirDiagnosticoUseCase;
import com.oficina.execution.application.usecase.execucao.ConcluirReparoUseCase;
import com.oficina.execution.application.usecase.execucao.ConsultarExecucaoUseCase;
import com.oficina.execution.application.usecase.execucao.ConsultarFilaUseCase;
import com.oficina.execution.application.usecase.execucao.IniciarDiagnosticoUseCase;
import com.oficina.execution.application.usecase.execucao.IniciarReparoUseCase;
import com.oficina.execution.application.usecase.execucao.RegistrarApontamentoUseCase;
import com.oficina.execution.application.usecase.execucao.RegistrarFalhaUseCase;
import com.oficina.execution.domain.repository.ExecucaoRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class UseCaseConfig {

    @Bean
    public RespostaComandos respostaComandos(PublicadorEventos publicador) {
        return new RespostaComandos(publicador);
    }

    @Bean
    public EnfileirarDiagnosticoUseCase enfileirarDiagnosticoUseCase(ExecucaoRepository r, RespostaComandos rc,
                                                                     UnidadeDeTrabalho u, Clock c) {
        return new EnfileirarDiagnosticoUseCase(r, rc, u, c);
    }

    @Bean
    public ReabrirDiagnosticoUseCase reabrirDiagnosticoUseCase(ExecucaoRepository r, RespostaComandos rc,
                                                               UnidadeDeTrabalho u, Clock c) {
        return new ReabrirDiagnosticoUseCase(r, rc, u, c);
    }

    @Bean
    public EnfileirarReparoUseCase enfileirarReparoUseCase(ExecucaoRepository r, RespostaComandos rc,
                                                           UnidadeDeTrabalho u, Clock c) {
        return new EnfileirarReparoUseCase(r, rc, u, c);
    }

    @Bean
    public CancelarExecucaoUseCase cancelarExecucaoUseCase(ExecucaoRepository r, RespostaComandos rc,
                                                           UnidadeDeTrabalho u, Clock c) {
        return new CancelarExecucaoUseCase(r, rc, u, c);
    }

    @Bean
    public ProcessarComandoUseCase processarComandoUseCase(RegistroMensagensProcessadas m,
                                                           EnfileirarDiagnosticoUseCase ed,
                                                           ReabrirDiagnosticoUseCase rd,
                                                           EnfileirarReparoUseCase er,
                                                           CancelarExecucaoUseCase ce,
                                                           RespostaComandos rc, UnidadeDeTrabalho u, Clock c) {
        return new ProcessarComandoUseCase(m, ed, rd, er, ce, rc, u, c);
    }

    @Bean
    public IniciarDiagnosticoUseCase iniciarDiagnosticoUseCase(ExecucaoRepository r, PublicadorEventos p,
                                                               UnidadeDeTrabalho u, Clock c) {
        return new IniciarDiagnosticoUseCase(r, p, u, c);
    }

    @Bean
    public ConcluirDiagnosticoUseCase concluirDiagnosticoUseCase(ExecucaoRepository r, PublicadorEventos p,
                                                                 UnidadeDeTrabalho u, Clock c) {
        return new ConcluirDiagnosticoUseCase(r, p, u, c);
    }

    @Bean
    public IniciarReparoUseCase iniciarReparoUseCase(ExecucaoRepository r, PublicadorEventos p,
                                                     UnidadeDeTrabalho u, Clock c) {
        return new IniciarReparoUseCase(r, p, u, c);
    }

    @Bean
    public ConcluirReparoUseCase concluirReparoUseCase(ExecucaoRepository r, PublicadorEventos p,
                                                       UnidadeDeTrabalho u, Clock c) {
        return new ConcluirReparoUseCase(r, p, u, c);
    }

    @Bean
    public RegistrarFalhaUseCase registrarFalhaUseCase(ExecucaoRepository r, PublicadorEventos p,
                                                       UnidadeDeTrabalho u, Clock c) {
        return new RegistrarFalhaUseCase(r, p, u, c);
    }

    @Bean
    public RegistrarApontamentoUseCase registrarApontamentoUseCase(ExecucaoRepository r, UnidadeDeTrabalho u,
                                                                   Clock c) {
        return new RegistrarApontamentoUseCase(r, u, c);
    }

    @Bean
    public AlterarPrioridadeUseCase alterarPrioridadeUseCase(ExecucaoRepository r, UnidadeDeTrabalho u) {
        return new AlterarPrioridadeUseCase(r, u);
    }

    @Bean
    public ConsultarFilaUseCase consultarFilaUseCase(ExecucaoRepository r) {
        return new ConsultarFilaUseCase(r);
    }

    @Bean
    public ConsultarExecucaoUseCase consultarExecucaoUseCase(ExecucaoRepository r) {
        return new ConsultarExecucaoUseCase(r);
    }
}
