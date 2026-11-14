package br.com.motorfraude.rules.fakes;

import br.com.motorfraude.rules.application.gateway.IdempotencyStore;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private record Row(String requestHash, boolean completed, StoredResponse response, Instant createdAt) {}

    private final Map<String, Row> rows = new HashMap<>();

    private static String id(String actor, String key) {
        return actor + "\u0000" + key;
    }

    @Override
    public synchronized boolean claim(String actor, String key, String requestHash, Instant now, Instant expiredBefore, Instant staleBefore) {
        var existing = rows.get(id(actor, key));
        boolean free = existing == null
                || existing.createdAt().isBefore(expiredBefore)
                || (!existing.completed() && existing.createdAt().isBefore(staleBefore));
        if (free) {
            rows.put(id(actor, key), new Row(requestHash, false, null, now));
        }
        return free;
    }

    @Override
    public synchronized Optional<Entry> find(String actor, String key) {
        return Optional.ofNullable(rows.get(id(actor, key))).map(r -> new Entry(r.requestHash(), r.completed(), r.response()));
    }

    @Override
    public synchronized void complete(String actor, String key, StoredResponse response, Instant now) {
        var row = rows.get(id(actor, key));
        rows.put(id(actor, key), new Row(row.requestHash(), true, response, row.createdAt()));
    }

    @Override
    public synchronized void release(String actor, String key) {
        var row = rows.get(id(actor, key));
        if (row != null && !row.completed()) {
            rows.remove(id(actor, key));
        }
    }

    public synchronized int size() {
        return rows.size();
    }
}
