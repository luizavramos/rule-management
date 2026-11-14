package br.com.motorfraude.rules.fakes;

import br.com.motorfraude.rules.application.gateway.UnitOfWork;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

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
