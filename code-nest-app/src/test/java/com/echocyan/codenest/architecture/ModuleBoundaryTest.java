package com.echocyan.codenest.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Map;
import java.util.Set;

/**
 * ADR-0001 的模块边界。模块间无环、framework/common 不依赖业务模块已由 Maven 依赖关系保证，
 * 这里只检查 Maven 管不到的两点：跨模块只能用 api 包；传递依赖也不能越过约定的依赖方向。
 */
@AnalyzeClasses(packages = ModuleBoundaryTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    static final String ROOT = "com.echocyan.codenest";

    /** 规格中约定的模块依赖：key 可以依赖 value 中的模块。 */
    private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.of(
            "user", Set.of("counter"),
            "counter", Set.of(),
            "article", Set.of("user", "counter"),
            "interaction", Set.of("article", "counter"),
            "social", Set.of("user", "article", "counter"),
            "notification", Set.of("user", "article", "interaction", "social"),
            "search", Set.of("article", "user"));

    @ArchTest
    static final ArchRule onlyApiPackagesAcrossModules = crossModuleDependencies(
            "only depend on other business modules through their api package",
            (own, target, targetPackage) -> isSameOrSubPackage(targetPackage, ROOT + "." + target + ".api"));

    @ArchTest
    static final ArchRule onlyDeclaredModuleDependencies = crossModuleDependencies(
            "only depend on business modules declared in the module dependency list",
            (own, target, targetPackage) -> ALLOWED_DEPENDENCIES.get(own).contains(target));

    private interface Allowed {
        boolean test(String ownModule, String targetModule, String targetPackage);
    }

    /**
     * 检查业务模块之间的每一条依赖，不满足 allowed 的记为违规。
     */
    private static ArchRule crossModuleDependencies(String description, Allowed allowed) {
        return classes().that().resideInAPackage(ROOT + "..").should(new ArchCondition<>(description) {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String own = moduleOf(origin.getPackageName());
                if (own == null) {
                    return;
                }
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getBaseComponentType().getPackageName();
                    String target = moduleOf(targetPackage);
                    if (target != null && !target.equals(own) && !allowed.test(own, target, targetPackage)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        });
    }

    private static String moduleOf(String packageName) {
        return ALLOWED_DEPENDENCIES.keySet().stream()
                .filter(module -> isSameOrSubPackage(packageName, ROOT + "." + module))
                .findFirst()
                .orElse(null);
    }

    private static boolean isSameOrSubPackage(String packageName, String parent) {
        return packageName.equals(parent) || packageName.startsWith(parent + ".");
    }
}
