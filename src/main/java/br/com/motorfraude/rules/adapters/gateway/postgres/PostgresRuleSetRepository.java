package br.com.motorfraude.rules.adapters.gateway.postgres;

import br.com.motorfraude.rules.application.gateway.RuleSetRepository;
import br.com.motorfraude.rules.domain.RuleSet;
import br.com.motorfraude.rules.domain.RuleSetItem;
import br.com.motorfraude.rules.domain.RuleStatus;
import br.com.motorfraude.rules.domain.Scoring;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class PostgresRuleSetRepository implements RuleSetRepository {

    private static final String HEADER = "select version, published_at, published_by, medium_threshold, high_threshold from rule_sets";

    private final JdbcClient jdbc;

    public PostgresRuleSetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RuleSet> findActive() {
        return jdbc.sql(HEADER + " order by version desc limit 1").query(this::ruleSet).optional();
    }

    @Override
    public OptionalLong activeVersion() {
        Long version = jdbc.sql("select max(version) from rule_sets").query(Long.class).single();
        return version == null ? OptionalLong.empty() : OptionalLong.of(version);
    }

    @Override
    public Optional<RuleSet> findByVersion(long version) {
        return jdbc.sql(HEADER + " where version = :v").param("v", version).query(this::ruleSet).optional();
    }

    @Override
    public void save(RuleSet ruleSet) {
        jdbc.sql("insert into rule_sets (version, published_at, published_by, medium_threshold, high_threshold) "
                        + "values (:version, :at, :by, :medium, :high)")
                .param("version", ruleSet.version()).param("at", Sql.timestamp(ruleSet.publishedAt()))
                .param("by", ruleSet.publishedBy()).param("medium", ruleSet.scoring().mediumThreshold())
                .param("high", ruleSet.scoring().highThreshold()).update();
        for (RuleSetItem item : ruleSet.items()) {
            jdbc.sql("insert into rule_set_items (rule_set_version, rule_id, rule_version, status) values (:v, :rule, :ruleVersion, :status)")
                    .param("v", ruleSet.version()).param("rule", item.ruleId()).param("ruleVersion", item.ruleVersion())
                    .param("status", item.status().name()).update();
        }
    }

    private RuleSet ruleSet(ResultSet rs, int row) throws SQLException {
        long version = rs.getLong("version");
        List<RuleSetItem> items = jdbc.sql("select rule_id, rule_version, status from rule_set_items where rule_set_version = :v order by rule_id")
                .param("v", version)
                .query((r, n) -> new RuleSetItem(r.getString("rule_id"), r.getInt("rule_version"), RuleStatus.valueOf(r.getString("status"))))
                .list();
        return new RuleSet(version, Sql.instant(rs, "published_at"), rs.getString("published_by"),
                new Scoring(rs.getInt("medium_threshold"), rs.getInt("high_threshold")), items);
    }
}
