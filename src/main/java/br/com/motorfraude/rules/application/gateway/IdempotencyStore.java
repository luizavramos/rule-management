package br.com.motorfraude.rules.application.gateway;

import java.time.Instant;
import java.util.Optional;

/**
 * Guarda as respostas por {@code Idempotency-Key}, separadas por usuário. Cada operação é atômica: duas requisições com
 * a mesma chave nunca conseguem reservá-la ao mesmo tempo.
 */
public interface IdempotencyStore {

    boolean claim(String actor, String key, String requestHash, Instant now, Instant expiredBefore, Instant staleBefore);

    Optional<Entry> find(String actor, String key);

    void complete(String actor, String key, StoredResponse response, Instant now);

    void release(String actor, String key);

    /** A resposta que será repetida: status, tipo, Location e corpo. */
    record StoredResponse(int status, String contentType, String location, String body) {}

    /** O que há guardado para a chave: o hash da requisição e, se concluída, a resposta. */
    record Entry(String requestHash, boolean completed, StoredResponse response) {}
}
