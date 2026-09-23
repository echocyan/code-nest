package com.echocyan.codenest.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.echocyan.codenest", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    private static final String ROOT = "com.echocyan.codenest";

    @ArchTest
    static final ArchRule onlyApiPackagesAcrossModules = ModuleRules.onlyApiPackagesAcrossModules(ROOT);

    @ArchTest
    static final ArchRule onlyDeclaredModuleDependencies = ModuleRules.onlyDeclaredModuleDependencies(ROOT);

    @ArchTest
    static final ArchRule infrastructureDoesNotDependOnBusinessModules =
            ModuleRules.infrastructureDoesNotDependOnBusinessModules(ROOT);

    @ArchTest
    static final ArchRule modulesAreFreeOfCycles = ModuleRules.modulesAreFreeOfCycles(ROOT);
}
