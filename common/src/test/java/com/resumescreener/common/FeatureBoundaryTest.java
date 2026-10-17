package com.resumescreener.common;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Feature boundaries: see docs/ARCHITECTURE.md "Adding a feature". */
@AnalyzeClasses(packages = "com.resumescreener.common", importOptions = ImportOption.DoNotIncludeTests.class)
class FeatureBoundaryTest {

    @ArchTest
    static final ArchRule features_do_not_depend_on_each_other_in_a_cycle =
            slices().matching("com.resumescreener.common.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule no_layer_first_packages =
            noClasses().should().resideInAnyPackage(
                    "..common.entity..", "..common.repository..", "..common.enums..", "..common.dto..");
}
