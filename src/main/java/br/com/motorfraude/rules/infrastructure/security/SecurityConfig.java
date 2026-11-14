package br.com.motorfraude.rules.infrastructure.security;

import br.com.motorfraude.rules.adapters.controller.IdempotencyFilter;
import br.com.motorfraude.rules.application.usecase.IdempotencyGuard;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SecurityConfig {

    private static final String AUTHOR = "RULE_AUTHOR";
    private static final String APPROVER = "RULE_APPROVER";
    private static final String READER = "RULE_READER";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(4);
    }

    @Bean
    UserDetailsService userDetailsService(SecurityProperties properties, PasswordEncoder encoder) {
        var users = properties.users().stream()
                .map(u -> User.withUsername(u.name()).password(encoder.encode(u.password())).roles(u.roles().toArray(String[]::new)).build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper json, IdempotencyGuard idempotencyGuard) throws Exception {
        var path = PathPatternRequestMatcher.withDefaults();
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> problem(json, response, HttpStatus.UNAUTHORIZED,
                                "Não autenticado", "Informe usuário e senha."))
                        .accessDeniedHandler((request, response, ex) -> problem(json, response, HttpStatus.FORBIDDEN,
                                "Acesso negado", "Seu perfil não permite esta operação.")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(path.matcher("/actuator/health/**"), path.matcher("/actuator/health"),
                                path.matcher("/actuator/prometheus")).permitAll()
                        .requestMatchers(path.matcher(HttpMethod.GET, "/v1/rules"), path.matcher(HttpMethod.GET, "/v1/rules/{ruleId}"),
                                path.matcher(HttpMethod.GET, "/v1/rule-sets/active")).hasAnyRole(AUTHOR, APPROVER, READER)
                        .requestMatchers(path.matcher(HttpMethod.POST, "/v1/rules/{ruleId}/versions/{version}:activate"),
                                path.matcher(HttpMethod.POST, "/v1/rule-sets/{version}:rollback"),
                                path.matcher(HttpMethod.PUT, "/v1/scoring")).hasRole(APPROVER)
                        .requestMatchers(path.matcher(HttpMethod.POST, "/v1/rules"), path.matcher(HttpMethod.POST, "/v1/rules:validate"),
                                path.matcher(HttpMethod.POST, "/v1/rules/{ruleId}/versions"),
                                path.matcher(HttpMethod.POST, "/v1/rules/{ruleId}/versions/{version}:shadow"),
                                path.matcher(HttpMethod.POST, "/v1/rules/{ruleId}:disable")).hasRole(AUTHOR)
                        .anyRequest().denyAll())
                .addFilterAfter(new IdempotencyFilter(idempotencyGuard, json), AuthorizationFilter.class);
        return http.build();
    }

    private static void problem(JsonMapper json, HttpServletResponse response, HttpStatus status, String title, String detail) throws IOException {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", "urn:motorfraude:problem:" + status.name().toLowerCase().replace('_', '-'));
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("errors", List.of());
        response.setStatus(status.value());
        response.setContentType(MediaType.valueOf("application/problem+json").toString());
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(body));
    }
}
