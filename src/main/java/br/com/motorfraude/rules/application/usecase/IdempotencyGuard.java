package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.port.out.IdempotencyStore;
import br.com.motorfraude.rules.application.port.out.IdempotencyStore.StoredResponse;
import java.time.Clock;
import java.time.Duration;

/**
 * Máquina de estados do {@code Idempotency-Key}. Primeira vez: executa e guarda a resposta. Mesma chave e mesma
 * requisição depois: repete a resposta sem executar. Mesma chave com outra requisição: recusa. Mesma chave enquanto a
 * primeira ainda executa: pede para tentar de novo. Só respostas de sucesso são guardadas; uma falha libera a chave.
 */
public final class IdempotencyGuard {

    /** Por quanto tempo a resposta é repetida. */
    public static final Duration RETENTION = Duration.ofHours(24);
    /** Depois disto, uma reserva sem resposta é considerada de um processo que morreu, e pode ser assumida. */
    public static final Duration STALE_AFTER = Duration.ofSeconds(60);

    /** O que fazer com a requisição. */
    public sealed interface Begin {
        /** Executar a operação e, ao fim, chamar {@link #complete} ou {@link #abandon}. */
        record Proceed() implements Begin {}

        /** Já foi feita: devolver esta resposta. */
        record Replay(StoredResponse response) implements Begin {}

        /** A primeira requisição com esta chave ainda está executando. */
        record InProgress() implements Begin {}

        /** A chave já foi usada com outra requisição. */
        record KeyReused() implements Begin {}
    }

    private final IdempotencyStore store;
    private final Clock clock;

    public IdempotencyGuard(IdempotencyStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Begin begin(String actor, String key, String requestHash) {
        var now = clock.instant();
        if (store.claim(actor, key, requestHash, now, now.minus(RETENTION), now.minus(STALE_AFTER))) {
            return new Begin.Proceed();
        }
        var existing = store.find(actor, key);
        if (existing.isEmpty()) {
            // Sumiu entre a tentativa e a leitura (foi liberada): tenta de novo, uma vez.
            return store.claim(actor, key, requestHash, now, now.minus(RETENTION), now.minus(STALE_AFTER))
                    ? new Begin.Proceed() : new Begin.InProgress();
        }
        var entry = existing.get();
        if (!entry.requestHash().equals(requestHash)) {
            return new Begin.KeyReused();
        }
        return entry.completed() ? new Begin.Replay(entry.response()) : new Begin.InProgress();
    }

    public void complete(String actor, String key, StoredResponse response) {
        store.complete(actor, key, response, clock.instant());
    }

    public void abandon(String actor, String key) {
        store.release(actor, key);
    }
}
