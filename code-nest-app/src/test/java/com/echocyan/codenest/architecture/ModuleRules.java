package com.echocyan.codenest.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;

/**
 * ADR-0001 的模块边界规则。root 参数化，便于用测试夹具验证规则本身。
 */
final class ModuleRules {

    static final List<String> BUSINESS_MODULES =
            List.of("user", "article", "counter", "interaction", "social", "notification", "search");

    private ModuleRules() {
    }

    /** 业务模块之间只能依赖对方的 api 包。 */
    static ArchRule onlyApiPackagesAcrossModules(String root) {
        return classes().that().resideInAPackage(root + "..")
                .should(new ArchCondition<>("only depend on other business modules through their api package") {
                    @Override
                    public void check(JavaClass origin, ConditionEvents events) {
                        String own = moduleOf(root, origin.getPackageName());
                        for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                            JavaClass target = dependency.getTargetClass().getBaseComponentType();
                            String targetModule = moduleOf(root, target.getPackageName());
                            if (targetModule == null || targetModule.equals(own)) {
                                continue;
                            }
                            String api = root + "." + targetModule + ".api";
                            if (!isSameOrSubPackage(target.getPackageName(), api)) {
                                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                            }
                        }
                    }
                })
                .allowEmptyShould(true);
    }

    /** common 与 framework 不能依赖任何业务模块。 */
    static ArchRule infrastructureDoesNotDependOnBusinessModules(String root) {
        return noClasses().that().resideInAnyPackage(root + ".common..", root + ".framework..")
                .should().dependOnClassesThat().resideInAnyPackage(BUSINESS_MODULES.stream()
                        .map(m -> root + "." + m + "..")
                        .toArray(String[]::new))
                .allowEmptyShould(true);
    }

    /** 模块之间（按顶层包划分）不能有依赖环。 */
    static ArchRule modulesAreFreeOfCycles(String root) {
        return slices().matching(root + ".(*)..").should().beFreeOfCycles().allowEmptyShould(true);
    }

    private static String moduleOf(String root, String packageName) {
        for (String module : BUSINESS_MODULES) {
            if (isSameOrSubPackage(packageName, root + "." + module)) {
                return module;
            }
        }
        return null;
    }

    private static boolean isSameOrSubPackage(String packageName, String parent) {
        return packageName.equals(parent) || packageName.startsWith(parent + ".");
    }
}
