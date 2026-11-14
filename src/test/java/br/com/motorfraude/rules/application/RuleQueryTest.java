package br.com.motorfraude.rules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.motorfraude.rules.application.port.out.RuleSetRepository;
import br.com.motorfraude.rules.application.usecase.GetActiveRuleSet;
import br.com.motorfraude.rules.application.usecase.GetRule;
import br.com.motorfraude.rules.application.usecase.ListRules;
import br.com.motorfraude.rules.domain.RuleSet;
import br.com.motorfraude.rules.fakes.Fixture;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T217/T220 — consultas: listar regras, histórico de uma regra e o conjunto ativo com cache por versão. */
class RuleQueryTest {

    private final Fixture f = new Fixture();

    @Test
    @DisplayName("listar devolve as regras em ordem de id; vazio sem regras")
    void listar() {
        var listRules = new ListRules(f.rules);
        assertThat(listRules.execute()).isEmpty();

        f.createRule.execute("analista", "R10", Fixture.content("a > 1"));
        f.createRule.execute("analista", "R02", Fixture.content("a > 1"));

        assertThat(listRules.execute()).extracting("ruleId").containsExactly("R02", "R10");
    }

    @Test
    @DisplayName("histórico: a regra com todas as versões em ordem; regra inexistente é 404")
    void historico() {
        var getRule = new GetRule(f.rules);
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));
        f.createRuleVersion.execute("analista", "R07", Fixture.content("a > 2"));

        var history = getRule.execute("R07");

        assertThat(history.rule().ruleId()).isEqualTo("R07");
        assertThat(history.versions()).extracting("version").containsExactly(1, 2);
        assertThatThrownBy(() -> getRule.execute("NAO")).isInstanceOf(RuleNotFoundException.class);
    }

    /** Conta quantas vezes o conjunto inteiro foi lido; a pergunta barata (só a versão) não conta. */
    private static final class CountingRuleSets implements RuleSetRepository {
        private final RuleSetRepository delegate;
        final AtomicInteger fullReads = new AtomicInteger();
        final AtomicInteger versionReads = new AtomicInteger();

        CountingRuleSets(RuleSetRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<RuleSet> findActive() {
            fullReads.incrementAndGet();
            return delegate.findActive();
        }

        @Override
        public OptionalLong activeVersion() {
            versionReads.incrementAndGet();
            return delegate.activeVersion();
        }

        @Override
        public Optional<RuleSet> findByVersion(long version) {
            return delegate.findByVersion(version);
        }

        @Override
        public void save(RuleSet ruleSet) {
            delegate.save(ruleSet);
        }
    }

    @Test
    @DisplayName("conjunto ativo: lê o conjunto inteiro uma vez e serve do cache enquanto a versão não muda")
    void cachePorVersao() {
        var counting = new CountingRuleSets(f.ruleSets);
        var cached = new GetActiveRuleSet(counting, f.rules);
        f.createAndActivate("R07", "a > 1");

        var first = cached.execute().orElseThrow();
        var second = cached.execute().orElseThrow();

        assertThat(second).isSameAs(first);
        assertThat(counting.fullReads.get()).isEqualTo(1);
        assertThat(counting.versionReads.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("ao publicar, a versão muda e o cache é refeito")
    void cacheInvalidaAoPublicar() {
        var counting = new CountingRuleSets(f.ruleSets);
        var cached = new GetActiveRuleSet(counting, f.rules);
        f.createAndActivate("R07", "a > 1");
        assertThat(cached.execute().orElseThrow().ruleSet().version()).isEqualTo(1);

        f.updateScoring.execute("coord", 30, 60);
        var after = cached.execute().orElseThrow();

        assertThat(after.ruleSet().version()).isEqualTo(2);
        assertThat(after.ruleSet().scoring().mediumThreshold()).isEqualTo(30);
        assertThat(counting.fullReads.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("versão atual: vazia sem publicação; o número da versão ativa depois")
    void versaoAtual() {
        var cached = new GetActiveRuleSet(f.ruleSets, f.rules);
        assertThat(cached.currentVersion()).isEmpty();
        assertThat(cached.execute()).isEmpty();

        f.createAndActivate("R07", "a > 1");

        assertThat(cached.currentVersion()).hasValue(1);
    }
}
