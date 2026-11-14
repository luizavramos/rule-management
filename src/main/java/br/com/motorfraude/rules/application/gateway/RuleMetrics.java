package br.com.motorfraude.rules.application.gateway;

public interface RuleMetrics {

    void published(String action);

    void compilationFailed(String kind);
}
