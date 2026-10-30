package com.oficina.execution.infrastructure.transaction;

import com.oficina.execution.application.port.UnidadeDeTrabalho;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class TransacaoMongo implements UnidadeDeTrabalho {

    private final TransactionTemplate template;

    public TransacaoMongo(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T executar(Supplier<T> trabalho) {
        return template.execute(status -> trabalho.get());
    }

    @Override
    public void executar(Runnable trabalho) {
        template.executeWithoutResult(status -> trabalho.run());
    }
}
