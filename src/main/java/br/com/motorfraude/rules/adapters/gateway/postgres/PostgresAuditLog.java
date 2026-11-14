package br.com.motorfraude.rules.adapters.gateway.postgres;

import br.com.motorfraude.rules.application.gateway.AuditLog;
import br.com.motorfraude.rules.domain.AuditEntry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public final class PostgresAuditLog implements AuditLog {

    private static final TypeReference<Map<String, Object>> SNAPSHOT = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public PostgresAuditLog(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void append(AuditEntry entry) {
        jdbc.sql("insert into audit_log (actor, action, entity, before, after, at) "
                        + "values (:actor, :action, :entity, cast(:before as jsonb), cast(:after as jsonb), :at)")
                .param("actor", entry.actor()).param("action", entry.action()).param("entity", entry.entity())
                .param("before", entry.before() == null ? null : json.writeValueAsString(entry.before()))
                .param("after", entry.after() == null ? null : json.writeValueAsString(entry.after()))
                .param("at", Sql.timestamp(entry.at())).update();
    }

    @Override
    public List<AuditEntry> recent(int limit) {
        return jdbc.sql("select actor, action, entity, before::text as before, after::text as after, at from audit_log order by id desc limit :limit")
                .param("limit", limit).query(this::entry).list();
    }

    private AuditEntry entry(ResultSet rs, int row) throws SQLException {
        return new AuditEntry(rs.getString("actor"), rs.getString("action"), rs.getString("entity"),
                snapshot(rs.getString("before")), snapshot(rs.getString("after")), Sql.instant(rs, "at"));
    }

    private Map<String, Object> snapshot(String text) {
        return text == null ? null : json.readValue(text, SNAPSHOT);
    }
}
