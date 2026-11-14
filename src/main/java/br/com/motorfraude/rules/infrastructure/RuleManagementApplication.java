package br.com.motorfraude.rules.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Ponto de entrada do rule-management (spec 002). Varre as camadas do serviço: adapters e infraestrutura. */
@SpringBootApplication(scanBasePackages = "br.com.motorfraude.rules")
@ConfigurationPropertiesScan
public class RuleManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuleManagementApplication.class, args);
    }
}
