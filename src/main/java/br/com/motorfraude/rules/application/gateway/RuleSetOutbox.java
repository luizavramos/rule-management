package br.com.motorfraude.rules.application.gateway;

import br.com.motorfraude.rules.application.usecase.ActiveRuleSet;

public interface RuleSetOutbox {

    void append(ActiveRuleSet published);
}
