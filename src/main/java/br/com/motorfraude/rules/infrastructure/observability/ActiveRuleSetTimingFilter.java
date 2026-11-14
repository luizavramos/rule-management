package br.com.motorfraude.rules.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActiveRuleSetTimingFilter extends OncePerRequestFilter {

    static final String METRIC = "rule.set.active.request";
    private static final String PATH = "/v1/rule-sets/active";

    private final MeterRegistry registry;

    public ActiveRuleSetTimingFilter(MeterRegistry registry) {
        this.registry = registry;
        timer(200);
        timer(304);
    }

    private Timer timer(int status) {
        return Timer.builder(METRIC)
                .description("Tempo de GET /v1/rule-sets/active (o que o motor consulta a cada 30 s), com a autenticação")
                .tag("status", Integer.toString(status))
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(10))
                .register(registry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod()) || !PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var sample = Timer.start(registry);
        int status = 500;
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            sample.stop(timer(status));
        }
    }
}
