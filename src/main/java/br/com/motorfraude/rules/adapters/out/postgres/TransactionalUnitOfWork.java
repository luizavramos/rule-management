package br.com.motorfraude.rules.adapters.out.postgres;

import br.com.motorfraude.rules.application.port.out.UnitOfWork;
import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transação SERIALIZABLE com nova tentativa: duas publicações simultâneas conflitam, uma falha (SQLSTATE 40001 ou
 * deadlock) e roda de novo, então as versões saem em sequência, sem repetição nem buraco.
 */
public final class TransactionalUnitOfWork implements UnitOfWork {

    private static final int MAX_ATTEMPTS = 40;

    private final TransactionTemplate template;

    public TransactionalUnitOfWork(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    @Override
    public <T> T serializable(Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return template.execute(status -> work.get());
            } catch (RuntimeException e) {
                if (attempt >= MAX_ATTEMPTS || !retryable(e)) {
                    throw e;
                }
                pause(attempt);
            }
        }
    }

    private static boolean retryable(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ConcurrencyFailureException) {
                return true;
            }
            if (t instanceof SQLException sql && ("40001".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState()))) {
                return true;
            }
            // Duas publicações calcularam a mesma versão: perdeu a corrida pela chave do conjunto.
            if (t instanceof DuplicateKeyException && String.valueOf(t.getMessage()).contains("rule_sets_pkey")) {
                return true;
            }
        }
        return false;
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(2, 12 + attempt * 2));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrompido enquanto esperava para repetir a transação", e);
        }
    }
}
