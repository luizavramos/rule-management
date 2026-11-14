package br.com.motorfraude.rules.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "br.com.motorfraude.rules")
@ConfigurationPropertiesScan
public class RuleManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuleManagementApplication.class, args);
    }
}
