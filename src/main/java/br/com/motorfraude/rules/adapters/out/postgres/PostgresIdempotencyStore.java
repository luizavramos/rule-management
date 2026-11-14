package br.com.motorfraude.rules.adapters.out.postgres;

import br.com.motorfraude.rules.application.port.out.IdempotencyStore;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Respostas por Idempotency-Key no Postgres. A reserva é um único INSERT ... ON CONFLICT: o banco decide quem ganha
 * quando duas requisições chegam juntas, sem transação longa nem trava na aplicação.
 */
public final class PostgresIdempotencyStore implements IdempotencyStore {

    private final JdbcClient jdbc;

    public PostgresIdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(String actor, String key, String requestHash, Instant now, Instant expiredBefore, Instant staleBefore) {
        return jdbc.sql("""
                insert into idempotency_keys (actor, idempotency_key, request_hash, state, created_at)
                values (:actor, :key, :hash, 'IN_PROGRESS', :now)
                on conflict (actor, idempotency_key) do update set
                    request_hash = excluded.request_hash, state = 'IN_PROGRESS', created_at = excluded.created_at,
                    response_status = null, response_content_type = null, response_location = null,
                    response_body = null, completed_at = null
                where idempotency_keys.created_at < :expiredBefore
                   or (idempotency_keys.state = 'IN_PROGRESS' and idempotency_keys.created_at < :staleBefore)
                """)
                .param("actor", actor).param("key", key).param("hash", requestHash).param("now", Sql.timestamp(now))
                .param("expiredBefore", Sql.timestamp(expiredBefore)).param("staleBefore", Sql.timestamp(staleBefore))
                .update() == 1;
    }

    @Override
    public Optional<Entry> find(String actor, String key) {
        return jdbc.sql("""
                select request_hash, state, response_status, response_content_type, response_location, response_body
                from idempotency_keys where actor = :actor and idempotency_key = :key
                """)
                .param("actor", actor).param("key", key)
                .query((rs, row) -> {
                    boolean completed = "COMPLETED".equals(rs.getString("state"));
                    var response = completed ? new StoredResponse(rs.getInt("response_status"), rs.getString("response_content_type"),
                            rs.getString("response_location"), rs.getString("response_body")) : null;
                    return new Entry(rs.getString("request_hash"), completed, response);
                }).optional();
    }

    @Override
    public void complete(String actor, String key, StoredResponse response, Instant now) {
        jdbc.sql("""
                update idempotency_keys set state = 'COMPLETED', response_status = :status, response_content_type = :type,
                    response_location = :location, response_body = :body, completed_at = :now
                where actor = :actor and idempotency_key = :key
                """)
                .param("status", response.status()).param("type", response.contentType()).param("location", response.location())
                .param("body", response.body()).param("now", Sql.timestamp(now)).param("actor", actor).param("key", key).update();
    }

    @Override
    public void release(String actor, String key) {
        jdbc.sql("delete from idempotency_keys where actor = :actor and idempotency_key = :key and state = 'IN_PROGRESS'")
                .param("actor", actor).param("key", key).update();
    }
}
