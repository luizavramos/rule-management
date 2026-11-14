package br.com.motorfraude.rules.application.port.out;

import java.time.Instant;
import java.util.Optional;

/**
 * Guarda as respostas por {@code Idempotency-Key}, separadas por usuário. Cada operação é atômica: duas requisições com
 * a mesma chave nunca conseguem reservá-la ao mesmo tempo.
 */
public interface IdempotencyStore {

    /**
     * Tenta reservar a chave (estado "em andamento"). Funciona se a chave é nova, se o registro é mais antigo que
     * {@code expiredBefore} (venceu a retenção) ou se está em andamento desde antes de {@code staleBefore} (o processo
     * que o reservou provavelmente morreu). Devolve false se outro registro válido já ocupa a chave.
     */
    boolean claim(String actor, String key, String requestHash, Instant now, Instant expiredBefore, Instant staleBefore);

    Optional<Entry> find(String actor, String key);

    /** Grava a resposta e marca a chave como concluída. */
    void complete(String actor, String key, StoredResponse response, Instant now);

    /** Libera uma chave que ainda está em andamento (a operação falhou), para a origem poder repetir. */
    void release(String actor, String key);

    /** A resposta que será repetida: status, tipo, Location e corpo. */
    record StoredResponse(int status, String contentType, String location, String body) {}

    /** O que há guardado para a chave: o hash da requisição e, se concluída, a resposta. */
    record Entry(String requestHash, boolean completed, StoredResponse response) {}
}
