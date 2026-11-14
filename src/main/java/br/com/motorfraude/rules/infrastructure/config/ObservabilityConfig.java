package br.com.motorfraude.rules.infrastructure.config;

import br.com.motorfraude.rules.application.gateway.RuleMetrics;
import br.com.motorfraude.rules.infrastructure.observability.ActiveRuleSetTimingFilter;
import br.com.motorfraude.rules.infrastructure.observability.RuleManagementMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ObservabilityConfig {

    @Bean
    RuleMetrics ruleMetrics(MeterRegistry registry) {
        return new RuleManagementMetrics(registry);
    }

    @Bean
    FilterRegistrationBean<ActiveRuleSetTimingFilter> activeRuleSetTimingFilter(MeterRegistry registry) {
        var registration = new FilterRegistrationBean<>(new ActiveRuleSetTimingFilter(registry));
        registration.addUrlPatterns("/v1/rule-sets/active");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
