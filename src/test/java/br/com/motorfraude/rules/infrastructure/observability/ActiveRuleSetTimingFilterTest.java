package br.com.motorfraude.rules.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ActiveRuleSetTimingFilterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ActiveRuleSetTimingFilter filter = new ActiveRuleSetTimingFilter(registry);

    private void call(String method, String path, int status) throws Exception {
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(status);
        filter.doFilter(new MockHttpServletRequest(method, path), new MockHttpServletResponse(), chain);
    }

    private long count(String status) {
        return registry.get("rule.set.active.request").tag("status", status).timer().count();
    }

    @Test
    @DisplayName("os temporizadores de 200 e 304 existem em zero desde a partida: sem isso o rate() do Prometheus não tem ponto de partida")
    void existemEmZero() {
        assertThat(count("200")).isZero();
        assertThat(count("304")).isZero();
        assertThat(registry.get("rule.set.active.request").tag("status", "304").timer().totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isZero();
    }

    @Test
    @DisplayName("GET /v1/rule-sets/active é cronometrado, separado por status (200 e 304)")
    void cronometraPorStatus() throws Exception {
        call("GET", "/v1/rule-sets/active", 200);
        call("GET", "/v1/rule-sets/active", 304);
        call("GET", "/v1/rule-sets/active", 304);

        assertThat(count("200")).isEqualTo(1);
        assertThat(count("304")).isEqualTo(2);
    }

    @Test
    @DisplayName("outras rotas e outros métodos não entram nessa métrica")
    void ignoraOutrasRotas() throws Exception {
        call("GET", "/v1/rules", 200);
        call("POST", "/v1/rule-sets/active", 405);
        call("GET", "/actuator/health", 200);

        assertThat(registry.find("rule.set.active.request").timers()).allMatch(t -> t.count() == 0);
    }

    @Test
    @DisplayName("uma resposta de erro (401, 404) também é cronometrada, com o seu status")
    void cronometraErros() throws Exception {
        call("GET", "/v1/rule-sets/active", 401);
        call("GET", "/v1/rule-sets/active", 404);

        assertThat(count("401")).isEqualTo(1);
        assertThat(count("404")).isEqualTo(1);
    }

    @Test
    @DisplayName("se a cadeia lança exceção, o tempo ainda é registrado e a exceção segue")
    void excecao() {
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("falhou");
        };

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> filter.doFilter(new MockHttpServletRequest("GET", "/v1/rule-sets/active"), new MockHttpServletResponse(), chain))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count("500")).isEqualTo(1);
    }
}
