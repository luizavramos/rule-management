package br.com.motorfraude.rules.infrastructure.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rules.security")
public record SecurityProperties(List<User> users) {

    public record User(String name, String password, List<String> roles) {}
}
