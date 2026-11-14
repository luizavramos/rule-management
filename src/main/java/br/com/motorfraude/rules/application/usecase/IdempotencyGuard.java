package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.gateway.IdempotencyStore;
import br.com.motorfraude.rules.application.gateway.IdempotencyStore.StoredResponse;
import java.time.Clock;
import java.time.Duration;

public final class IdempotencyGuard {

    public static final Duration RETENTION = Duration.ofHours(24);
    public static final Duration STALE_AFTER = Duration.ofSeconds(60);

    public sealed interface Begin {
        record Proceed() implements Begin {}

        record Replay(StoredResponse response) implements Begin {}

        record InProgress() implements Begin {}

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
