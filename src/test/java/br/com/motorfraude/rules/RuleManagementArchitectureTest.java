package br.com.motorfraude.rules;

import br.com.motorfraude.commons.architecture.CleanArchitectureRules;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regra de dependência da Clean Architecture (constituição I e VIII) aplicada ao rule-management. */
class RuleManagementArchitectureTest {

    private static final String BASE = "br.com.motorfraude.rules";

    @Test
    @DisplayName("domain ← application ← adapters ← infrastructure, sem frameworks nas camadas internas e sem relógio direto")
    void arquitetura() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .withImportOption(new ImportOption.DoNotIncludeJars())
                .importPackages(BASE);

        CleanArchitectureRules.assertAll(classes, BASE);
    }

    @Test
    @DisplayName("o nosso código usa só Jackson 3: o Jackson 2 está no classpath apenas por causa do Flyway")
    void semJackson2() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .withImportOption(new ImportOption.DoNotIncludeJars())
                .importPackages(BASE);

        ArchRuleDefinition.noClasses()
                .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson.databind..", "com.fasterxml.jackson.core..")
                .check(classes);
    }
}
