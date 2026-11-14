package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.domain.Rule;
import br.com.motorfraude.rules.domain.RuleVersion;
import java.util.List;

/** Uma regra com todas as suas versões, da mais antiga para a mais nova. */
public record RuleHistory(Rule rule, List<RuleVersion> versions) {}
