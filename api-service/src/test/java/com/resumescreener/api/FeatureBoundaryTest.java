package com.resumescreener.api;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
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

    // Every call, logins and deletes included, answers with the success/message/data envelope, never an empty body
    @ArchTest
    static final ArchRule endpoints_answer_with_an_api_response_body =
            methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                    .and().areMetaAnnotatedWith(RequestMapping.class)
                    .should(new ArchCondition<JavaMethod>("return ResponseEntity<ApiResponse<...>>") {
                        @Override
                        public void check(JavaMethod method, ConditionEvents events) {
                            String type = method.reflect().getGenericReturnType().getTypeName();
                            boolean ok = type.startsWith("org.springframework.http.ResponseEntity<com.resumescreener.api.common.ApiResponse<");
                            events.add(new SimpleConditionEvent(method, ok, method.getFullName() + " returns " + type));
                        }
                    });
}
