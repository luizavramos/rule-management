package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.RuleNotFoundException;
import br.com.motorfraude.rules.application.RuleSetInvalidException;
import br.com.motorfraude.rules.application.port.out.AuditLog;
import br.com.motorfraude.rules.application.port.out.RuleRepository;
import br.com.motorfraude.rules.application.port.out.RuleSetRepository;
import br.com.motorfraude.rules.application.port.out.RuleValidator;
import br.com.motorfraude.rules.application.port.out.UnitOfWork;
import br.com.motorfraude.rules.domain.AuditEntry;
import br.com.motorfraude.rules.domain.RuleSet;
import br.com.motorfraude.rules.domain.RuleSetItem;
import br.com.motorfraude.rules.domain.RuleVersion;
import br.com.motorfraude.rules.domain.Scoring;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Núcleo de toda mudança (plano 002, §4.2). Numa única transação serializável: lê o conjunto ativo, aplica a mudança,
 * compila tudo e, só se compilou, grava os status das versões, o conjunto N+1 e a auditoria. Se qualquer passo falhar,
 * nada é publicado.
 */
public final class RuleSetPublisher {

    private static final Scoring DEFAULT_SCORING = new Scoring(40, 70);

    /** A mudança pedida: itens e scoring do novo conjunto, e as versões cujo status muda. */
    public record Change(String action, String entity, List<RuleSetItem> items, Scoring scoring, List<RuleVersion> updatedVersions) {}

    /** Calcula a mudança a partir do conjunto ativo; roda dentro da transação. */
    @FunctionalInterface
    public interface Mutation {
        Change apply(RuleSet current);
    }

    private final RuleRepository rules;
    private final RuleSetRepository ruleSets;
    private final AuditLog audit;
    private final RuleValidator validator;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RuleSetPublisher(RuleRepository rules, RuleSetRepository ruleSets, AuditLog audit, RuleValidator validator,
            UnitOfWork unitOfWork, Clock clock) {
        this.rules = rules;
        this.ruleSets = ruleSets;
        this.audit = audit;
        this.validator = validator;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public RuleSet publish(String actor, Mutation mutation) {
        return unitOfWork.serializable(() -> {
            var now = clock.instant();
            var current = ruleSets.findActive().orElseGet(() -> new RuleSet(0, now, "system", DEFAULT_SCORING, List.of()));
            Change change = mutation.apply(current);

            var items = new ArrayList<>(change.items());
            items.sort(Comparator.comparing(RuleSetItem::ruleId));
            var updates = new HashMap<String, RuleVersion>();
            change.updatedVersions().forEach(v -> updates.put(v.ruleId() + "#" + v.version(), v));

            var effective = new ArrayList<RuleVersion>();
            for (RuleSetItem item : items) {
                var version = updates.get(item.ruleId() + "#" + item.ruleVersion());
                if (version == null) {
                    version = rules.findVersion(item.ruleId(), item.ruleVersion())
                            .orElseThrow(() -> RuleNotFoundException.version(item.ruleId(), item.ruleVersion()));
                }
                if (version.status() != item.status()) {
                    throw new IllegalStateException("status da versão " + item.ruleId() + " v" + item.ruleVersion()
                            + " (" + version.status() + ") difere do item do conjunto (" + item.status() + ")");
                }
                effective.add(version);
            }
            var issues = validator.validateSet(change.scoring(), effective);
            if (!issues.isEmpty()) {
                throw new RuleSetInvalidException(issues);
            }

            change.updatedVersions().forEach(rules::saveVersion);
            var published = new RuleSet(current.version() + 1, now, actor, change.scoring(), items);
            ruleSets.save(published);
            audit.append(new AuditEntry(actor, change.action(), change.entity(), AuditMaps.of(current), AuditMaps.of(published), now));
            return published;
        });
    }

    /** Itens do conjunto atual sem a regra dada (ponto de partida para trocar ou remover um item). */
    static List<RuleSetItem> itemsWithout(RuleSet current, String ruleId) {
        var items = new ArrayList<>(current.items());
        items.removeIf(i -> i.ruleId().equals(ruleId));
        return items;
    }

}
