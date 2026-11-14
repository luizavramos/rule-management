package br.com.motorfraude.rules.adapters.gateway.postgres;

import br.com.motorfraude.rules.application.gateway.RuleRepository;
import br.com.motorfraude.rules.domain.Rule;
import br.com.motorfraude.rules.domain.RuleStatus;
import br.com.motorfraude.rules.domain.RuleVersion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Regras e versões no Postgres. O banco impede mudar o conteúdo de uma versão (gatilho), só o status e o aprovador. */
public final class PostgresRuleRepository implements RuleRepository {

    private static final TypeReference<Map<String, Object>> PARAMS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Map<String, Object>>> OVERRIDES = new TypeReference<>() {};

    private static final String VERSION_COLUMNS = "rule_id, version, expression, params::text as params, "
            + "param_overrides::text as param_overrides, weight, reason_template, status, author, approved_by, created_at";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public PostgresRuleRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean insertRule(Rule rule) {
        return jdbc.sql("insert into rules (rule_id, created_by, created_at) values (:id, :by, :at) on conflict do nothing")
                .param("id", rule.ruleId()).param("by", rule.createdBy()).param("at", Sql.timestamp(rule.createdAt()))
                .update() == 1;
    }

    @Override
    public Optional<Rule> findRule(String ruleId) {
        return jdbc.sql("select rule_id, created_by, created_at from rules where rule_id = :id").param("id", ruleId)
                .query(PostgresRuleRepository::rule).optional();
    }

    @Override
    public List<Rule> listRules() {
        return jdbc.sql("select rule_id, created_by, created_at from rules order by rule_id").query(PostgresRuleRepository::rule).list();
    }

    @Override
    public int nextVersionNumber(String ruleId) {
        return jdbc.sql("select coalesce(max(version), 0) + 1 from rule_versions where rule_id = :id").param("id", ruleId)
                .query(Integer.class).single();
    }

    @Override
    public void saveVersion(RuleVersion v) {
        // No conflito atualiza todas as colunas: se o conteúdo mudou, o gatilho do banco recusa (versão imutável).
        jdbc.sql("""
                insert into rule_versions (rule_id, version, expression, params, param_overrides, weight, reason_template,
                                           status, author, approved_by, created_at)
                values (:ruleId, :version, :expression, cast(:params as jsonb), cast(:overrides as jsonb), :weight, :reason,
                        :status, :author, :approvedBy, :createdAt)
                on conflict (rule_id, version) do update set
                    expression = excluded.expression, params = excluded.params, param_overrides = excluded.param_overrides,
                    weight = excluded.weight, reason_template = excluded.reason_template, status = excluded.status,
                    author = excluded.author, approved_by = excluded.approved_by, created_at = excluded.created_at
                """)
                .param("ruleId", v.ruleId()).param("version", v.version()).param("expression", v.expression())
                .param("params", json.writeValueAsString(v.params())).param("overrides", json.writeValueAsString(v.paramOverrides()))
                .param("weight", v.weight()).param("reason", v.reasonTemplate()).param("status", v.status().name())
                .param("author", v.author()).param("approvedBy", v.approvedBy()).param("createdAt", Sql.timestamp(v.createdAt()))
                .update();
    }

    @Override
    public Optional<RuleVersion> findVersion(String ruleId, int version) {
        return jdbc.sql("select " + VERSION_COLUMNS + " from rule_versions where rule_id = :id and version = :v")
                .param("id", ruleId).param("v", version).query(this::version).optional();
    }

    @Override
    public List<RuleVersion> listVersions(String ruleId) {
        return jdbc.sql("select " + VERSION_COLUMNS + " from rule_versions where rule_id = :id order by version")
                .param("id", ruleId).query(this::version).list();
    }

    private static Rule rule(ResultSet rs, int row) throws SQLException {
        return new Rule(rs.getString("rule_id"), rs.getString("created_by"), Sql.instant(rs, "created_at"));
    }

    private RuleVersion version(ResultSet rs, int row) throws SQLException {
        return new RuleVersion(rs.getString("rule_id"), rs.getInt("version"), rs.getString("expression"),
                json.readValue(rs.getString("params"), PARAMS), json.readValue(rs.getString("param_overrides"), OVERRIDES),
                rs.getInt("weight"), rs.getString("reason_template"), RuleStatus.valueOf(rs.getString("status")),
                rs.getString("author"), rs.getString("approved_by"), Sql.instant(rs, "created_at"));
    }
}
