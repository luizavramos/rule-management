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

/** Quem pode o quê (plano 002, §4.4). HTTP Basic sem sessão: é uma API para pessoas na demo e para o motor. */
@Configuration
public class SecurityConfig {

    private static final String AUTHOR = "RULE_AUTHOR";
    private static final String APPROVER = "RULE_APPROVER";
    private static final String READER = "RULE_READER";

    /**
     * BCrypt de custo baixo (4). O HTTP Basic confere a senha a cada requisição: com o custo padrão (10) medi ~75 ms por
     * chamada, o que estourava o p99 de 10 ms do GET /active (SC-002). Os usuários são locais e a senha já está na
     * configuração; em produção a identidade vem do OIDC no ALB [DOC], sem senha neste serviço.
     */
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
                        // Saúde e métricas sem login: o orquestrador e o Prometheus não têm credencial (rede interna).
                        .requestMatchers(path.matcher("/actuator/health/**"), path.matcher("/actuator/health"),
                                path.matcher("/actuator/prometheus")).permitAll()
                        // Leitura: qualquer perfil, inclusive o motor (conjunto ativo).
                        .requestMatchers(path.matcher(HttpMethod.GET, "/v1/rules"), path.matcher(HttpMethod.GET, "/v1/rules/{ruleId}"),
                                path.matcher(HttpMethod.GET, "/v1/rule-sets/active")).hasAnyRole(AUTHOR, APPROVER, READER)
                        // Quem ativa, reverte ou muda as faixas aprova: é o perfil APPROVER.
                        .requestMatchers(path.matcher(HttpMethod.POST, "/v1/rules/{ruleId}/versions/{version}:activate"),
                                path.matcher(HttpMethod.POST, "/v1/rule-sets/{version}:rollback"),
                                path.matcher(HttpMethod.PUT, "/v1/scoring")).hasRole(APPROVER)
                        // Quem cria, valida, põe em sombra e desliga (kill switch) é o perfil AUTHOR.
                        .requestMatchers(path.matcher(HttpMethod.POST, "/v1/rules"), path.matcher(HttpMethod.POST, "/v1/rules:validate"),
                                path.matcher(HttpMethod.POST, "/v1/rules/{ruleId}/versions"),
                                path.matcher(HttpMethod.POST, "/v1/rules/{ruleId}/versions/{version}:shadow"),
                                path.matcher(HttpMethod.POST, "/v1/rules/{ruleId}:disable")).hasRole(AUTHOR)
                        .anyRequest().denyAll())
                // Depois da autorização: a Idempotency-Key vale por usuário e uma requisição negada não gasta a chave.
                // O filtro não é um @Bean de propósito, para o Boot não registrá-lo uma segunda vez fora da cadeia.
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
