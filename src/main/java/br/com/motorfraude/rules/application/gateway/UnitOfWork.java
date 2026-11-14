package br.com.motorfraude.rules.application.gateway;

import java.util.function.Supplier;

/**
 * Executa o trabalho numa transação serializável: duas publicações simultâneas nunca geram a mesma versão nem
 * deixam buraco na sequência. Se o trabalho lançar, nada do que ele gravou vale.
 */
public interface UnitOfWork {

    <T> T serializable(Supplier<T> work);
}
