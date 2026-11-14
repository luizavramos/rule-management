package br.com.motorfraude.rules.application.gateway;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyStore {

    boolean claim(String actor, String key, String requestHash, Instant now, Instant expiredBefore, Instant staleBefore);

    Optional<Entry> find(String actor, String key);

    void complete(String actor, String key, StoredResponse response, Instant now);

    void release(String actor, String key);

    record StoredResponse(int status, String contentType, String location, String body) {}

    record Entry(String requestHash, boolean completed, StoredResponse response) {}
}
