package br.com.motorfraude.rules.application.gateway;

import java.util.function.Supplier;

public interface UnitOfWork {

    <T> T serializable(Supplier<T> work);
}
