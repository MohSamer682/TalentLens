package com.resumescreener.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** Feature boundaries: see docs/ARCHITECTURE.md "Adding a feature". */
@AnalyzeClasses(packages = "com.resumescreener.api", importOptions = ImportOption.DoNotIncludeTests.class)
class FeatureBoundaryTest {

    @ArchTest
    static final ArchRule features_do_not_depend_on_each_other_in_a_cycle =
            slices().matching("com.resumescreener.api.(*)..").should().beFreeOfCycles();

    // config wires the features together, so it may use them; the reverse would hide a cycle behind "shared" config
    @ArchTest
    static final ArchRule features_do_not_reach_into_config =
            noClasses().that().resideOutsideOfPackages("..api.config..", "..api")
                    .should().dependOnClassesThat().resideInAPackage("..api.config..");
}
