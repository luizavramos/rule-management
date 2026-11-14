package br.com.motorfraude.rules.adapters.gateway.postgres;

import br.com.motorfraude.rules.adapters.presenter.ApiResponses.ActiveRuleSetResponse;
import br.com.motorfraude.rules.application.gateway.RuleSetOutbox;
import br.com.motorfraude.rules.application.usecase.ActiveRuleSet;
import br.com.motorfraude.rules.domain.RuleSet;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

public final class PostgresRuleSetOutbox implements RuleSetOutbox {

    static final String TOPIC = "rule-sets.v1";
    static final String KEY = "active";
    static final String TYPE = "RuleSetPublished";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public PostgresRuleSetOutbox(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void append(ActiveRuleSet event) {
        var set = event.ruleSet();
        var published = new ActiveRuleSet(new RuleSet(set.version(), set.publishedAt().truncatedTo(ChronoUnit.MICROS), set.publishedBy(),
                set.scoring(), set.items()), event.versions());
        jdbc.sql("insert into outbox (id, aggregatetype, aggregateid, type, payload, created_at) "
                        + "values (:id, :topic, :key, :type, cast(:payload as jsonb), :at)")
                .param("id", UUID.randomUUID()).param("topic", TOPIC).param("key", KEY).param("type", TYPE)
                .param("payload", json.writeValueAsString(ActiveRuleSetResponse.of(published)))
                .param("at", Sql.timestamp(published.ruleSet().publishedAt())).update();
    }
}
