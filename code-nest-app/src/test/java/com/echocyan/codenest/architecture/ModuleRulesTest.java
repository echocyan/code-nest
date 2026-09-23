package com.echocyan.codenest.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

/**
 * 用夹具验证规则本身会报出违规，避免规则写错后永远是绿的。
 */
class ModuleRulesTest {

    private static final String FIXTURE = "com.echocyan.codenest.architecture.fixture";

    @Test
    void reportsDependencyOnAnotherModulesInternalsButAllowsItsApi() {
        String root = FIXTURE + ".boundary";
        JavaClasses classes = new ClassFileImporter().importPackages(root);

        EvaluationResult result = ModuleRules.onlyApiPackagesAcrossModules(root).evaluate(classes);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains("ViaInternals"))
                .noneMatch(line -> line.contains("ViaApi"));
    }

    @Test
    void reportsCyclesBetweenModules() {
        String root = FIXTURE + ".cycle";
        JavaClasses classes = new ClassFileImporter().importPackages(root);

        EvaluationResult result = ModuleRules.modulesAreFreeOfCycles(root).evaluate(classes);

        assertThat(result.hasViolation()).isTrue();
    }
}
