package br.com.motorfraude.rules.infrastructure.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Usuários locais, em memória (FR-009). Em produção a identidade vem do OIDC no ALB [DOC]. Papéis: RULE_AUTHOR (cria,
 * valida, sombra, desliga), RULE_APPROVER (ativa, reverte, muda o scoring) e RULE_READER (só leitura; o motor).
 */
@ConfigurationProperties("rules.security")
public record SecurityProperties(List<User> users) {

    public record User(String name, String password, List<String> roles) {}
}
