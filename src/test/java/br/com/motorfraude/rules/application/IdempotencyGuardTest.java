package br.com.motorfraude.rules.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.motorfraude.commons.time.MutableClock;
import br.com.motorfraude.rules.application.gateway.IdempotencyStore.StoredResponse;
import br.com.motorfraude.rules.application.usecase.IdempotencyGuard;
import br.com.motorfraude.rules.application.usecase.IdempotencyGuard.Begin;
import br.com.motorfraude.rules.fakes.InMemoryIdempotencyStore;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Idempotency-Key — a máquina de estados: reservar, repetir a resposta, recusar chave reaproveitada, expirar. */
class IdempotencyGuardTest {

    private static final StoredResponse RESPONSE = new StoredResponse(201, "application/json", "/v1/rules/R07/versions/1", "{\"ok\":true}");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-14T12:00:00Z"));
    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
    private final IdempotencyGuard guard = new IdempotencyGuard(store, clock);

    @Test
    @DisplayName("chave nova: pode executar")
    void nova() {
        assertThat(guard.begin("analista", "k1", "h1")).isInstanceOf(Begin.Proceed.class);
    }

    @Test
    @DisplayName("depois de concluída, a mesma chave com a mesma requisição repete a resposta guardada")
    void repete() {
        guard.begin("analista", "k1", "h1");
        guard.complete("analista", "k1", RESPONSE);

        var result = guard.begin("analista", "k1", "h1");

        assertThat(result).isInstanceOfSatisfying(Begin.Replay.class, r -> assertThat(r.response()).isEqualTo(RESPONSE));
    }

    @Test
    @DisplayName("a mesma chave com outra requisição é recusada, concluída ou não")
    void chaveReaproveitada() {
        guard.begin("analista", "k1", "h1");
        assertThat(guard.begin("analista", "k1", "h2")).isInstanceOf(Begin.KeyReused.class);

        guard.complete("analista", "k1", RESPONSE);
        assertThat(guard.begin("analista", "k1", "h2")).isInstanceOf(Begin.KeyReused.class);
    }

    @Test
    @DisplayName("a mesma chave ainda em andamento: devolve 'em andamento', sem executar de novo")
    void emAndamento() {
        guard.begin("analista", "k1", "h1");

        assertThat(guard.begin("analista", "k1", "h1")).isInstanceOf(Begin.InProgress.class);
    }

    @Test
    @DisplayName("em andamento há mais de 60 s o processo morreu: outra requisição assume")
    void reservaVelha() {
        guard.begin("analista", "k1", "h1");
        clock.advance(Duration.ofSeconds(59));
        assertThat(guard.begin("analista", "k1", "h1")).isInstanceOf(Begin.InProgress.class);

        clock.advance(Duration.ofSeconds(2));

        assertThat(guard.begin("analista", "k1", "h1")).isInstanceOf(Begin.Proceed.class);
    }

    @Test
    @DisplayName("abandonar (a operação falhou) libera a chave para repetir, até com outro corpo")
    void abandonar() {
        guard.begin("analista", "k1", "h1");

        guard.abandon("analista", "k1");

        assertThat(guard.begin("analista", "k1", "h2")).isInstanceOf(Begin.Proceed.class);
    }

    @Test
    @DisplayName("abandonar não apaga uma resposta já concluída")
    void abandonarConcluida() {
        guard.begin("analista", "k1", "h1");
        guard.complete("analista", "k1", RESPONSE);

        guard.abandon("analista", "k1");

        assertThat(guard.begin("analista", "k1", "h1")).isInstanceOf(Begin.Replay.class);
    }

    @Test
    @DisplayName("passadas 24 h a resposta venceu: a chave pode ser usada de novo")
    void retencao() {
        guard.begin("analista", "k1", "h1");
        guard.complete("analista", "k1", RESPONSE);
        clock.advance(Duration.ofHours(23));
        assertThat(guard.begin("analista", "k1", "h1")).isInstanceOf(Begin.Replay.class);

        clock.advance(Duration.ofHours(2));

        assertThat(guard.begin("analista", "k1", "h1")).isInstanceOf(Begin.Proceed.class);
    }

    @Test
    @DisplayName("cada usuário tem as suas chaves: a mesma chave de outro usuário não colide")
    void porUsuario() {
        guard.begin("analista", "k1", "h1");
        guard.complete("analista", "k1", RESPONSE);

        assertThat(guard.begin("coordenador", "k1", "h9")).isInstanceOf(Begin.Proceed.class);
        assertThat(guard.begin("analista", "k1", "h1")).isInstanceOf(Begin.Replay.class);
    }
}
