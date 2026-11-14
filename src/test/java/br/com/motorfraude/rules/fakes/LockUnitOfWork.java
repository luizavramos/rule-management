package br.com.motorfraude.rules.fakes;

import br.com.motorfraude.rules.application.port.out.UnitOfWork;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Fake de {@link UnitOfWork}: um trinco global faz as transações serem de fato uma de cada vez. */
public final class LockUnitOfWork implements UnitOfWork {

    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public <T> T serializable(Supplier<T> work) {
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }
}
