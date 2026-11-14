package br.com.motorfraude.rules.adapters.gateway.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.motorfraude.rules.application.gateway.IdempotencyStore.StoredResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Idempotency-Key — o store no Postgres: reserva atômica, resposta guardada, expiração e liberação. */
class IdempotencyStoreIT extends PostgresSupport {

    private static final Instant T0 = Instant.parse("2026-11-14T12:00:00Z");
    private static final Instant NEVER_EXPIRED = T0.minus(Duration.ofDays(30));
    private static final Instant NEVER_STALE = T0.minus(Duration.ofDays(30));

    private final PostgresIdempotencyStore store = new PostgresIdempotencyStore(jdbc);

    private boolean claim(String actor, String key, String hash, Instant now, Instant expiredBefore, Instant staleBefore) {
        return store.claim(actor, key, hash, now, expiredBefore, staleBefore);
    }

    @Test
    @DisplayName("a primeira reserva funciona; a segunda, com a chave ocupada, não; e a entrada fica em andamento")
    void reserva() {
        assertThat(claim("analista", "k1", "h1", T0, NEVER_EXPIRED, NEVER_STALE)).isTrue();
        assertThat(claim("analista", "k1", "h1", T0, NEVER_EXPIRED, NEVER_STALE)).isFalse();

        var entry = store.find("analista", "k1").orElseThrow();
        assertThat(entry.requestHash()).isEqualTo("h1");
        assertThat(entry.completed()).isFalse();
        assertThat(entry.response()).isNull();
        assertThat(store.find("analista", "outra")).isEmpty();
    }

    @Test
    @DisplayName("concluir guarda status, tipo, Location e corpo (com acentos) para repetir depois")
    void concluir() {
        claim("analista", "k1", "h1", T0, NEVER_EXPIRED, NEVER_STALE);
        var response = new StoredResponse(201, "application/json", "/v1/rules/R07/versions/1", "{\"motivo\":\"transações\"}");

        store.complete("analista", "k1", response, T0.plusSeconds(1));

        var entry = store.find("analista", "k1").orElseThrow();
        assertThat(entry.completed()).isTrue();
        assertThat(entry.response()).isEqualTo(response);
        assertThat(claim("analista", "k1", "h1", T0.plusSeconds(2), NEVER_EXPIRED, NEVER_STALE)).isFalse();
    }

    @Test
    @DisplayName("concluir sem Location guarda nulo")
    void semLocation() {
        claim("analista", "k1", "h1", T0, NEVER_EXPIRED, NEVER_STALE);

        store.complete("analista", "k1", new StoredResponse(200, "application/json", null, "{}"), T0);

        assertThat(store.find("analista", "k1").orElseThrow().response().location()).isNull();
    }

    @Test
    @DisplayName("liberar remove a reserva em andamento, mas não uma resposta já concluída")
    void liberar() {
        claim("analista", "k1", "h1", T0, NEVER_EXPIRED, NEVER_STALE);
        store.release("analista", "k1");
        assertThat(store.find("analista", "k1")).isEmpty();

        claim("analista", "k2", "h2", T0, NEVER_EXPIRED, NEVER_STALE);
        store.complete("analista", "k2", new StoredResponse(200, "application/json", null, "{}"), T0);
        store.release("analista", "k2");
        assertThat(store.find("analista", "k2")).isPresent();
    }

    @Test
    @DisplayName("reserva velha em andamento é assumida por outra requisição, que troca o hash")
    void reservaVelha() {
        claim("analista", "k1", "h1", T0, NEVER_EXPIRED, NEVER_STALE);

        boolean assumida = claim("analista", "k1", "h2", T0.plusSeconds(61), NEVER_EXPIRED, T0.plusSeconds(1));

        assertThat(assumida).isTrue();
        assertThat(store.find("analista", "k1").orElseThrow().requestHash()).isEqualTo("h2");
    }

    @Test
    @DisplayName("resposta com mais de 24 h venceu: a chave é reutilizável e a resposta antiga some")
    void retencao() {
        claim("analista", "k1", "h1", T0, NEVER_EXPIRED, NEVER_STALE);
        store.complete("analista", "k1", new StoredResponse(201, "application/json", null, "{\"velha\":true}"), T0);

        boolean reutilizada = claim("analista", "k1", "h9", T0.plus(Duration.ofHours(25)), T0.plus(Duration.ofHours(1)), NEVER_STALE);

        assertThat(reutilizada).isTrue();
        var entry = store.find("analista", "k1").orElseThrow();
        assertThat(entry.requestHash()).isEqualTo("h9");
        assertThat(entry.completed()).isFalse();
        assertThat(entry.response()).isNull();
    }

    @Test
    @DisplayName("cada usuário tem as suas chaves")
    void porUsuario() {
        assertThat(claim("analista", "k1", "h1", T0, NEVER_EXPIRED, NEVER_STALE)).isTrue();
        assertThat(claim("coordenador", "k1", "h1", T0, NEVER_EXPIRED, NEVER_STALE)).isTrue();
    }

    @Test
    @DisplayName("dez reservas simultâneas da mesma chave: só uma vence")
    void concorrencia() throws Exception {
        try (var pool = Executors.newFixedThreadPool(10)) {
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(() -> claim("analista", "corrida", "h1", T0, NEVER_EXPIRED, NEVER_STALE)));
            }
            int vencedoras = 0;
            for (var future : futures) {
                if (future.get()) {
                    vencedoras++;
                }
            }
            assertThat(vencedoras).isEqualTo(1);
        }
    }
}
