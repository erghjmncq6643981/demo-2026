package com.chandler.learning.agent.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** 非 AI 业务域共同遵守的依赖方向。 */
@AnalyzeClasses(packages = "com.chandler.learning.agent")
class LayeredPackageArchitectureTest {

    @ArchTest
    static final ArchRule CONTROLLERS_DO_NOT_ACCESS_PERSISTENCE = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().resideInAnyPackage("..mapper..", "..infrastructure..");

    @ArchTest
    static final ArchRule API_DOES_NOT_ACCESS_PERSISTENCE = noClasses()
            .that().resideInAnyPackage("..identity.api..", "..vocabulary.api..", "..learning.api..",
                    "..reading.api..", "..task.api..", "..system.api..")
            .should().dependOnClassesThat().resideInAnyPackage("..mapper..", "..infrastructure..");

    @ArchTest
    static final ArchRule LEGACY_HORIZONTAL_PACKAGES_ARE_NOT_RECREATED = noClasses()
            .should().resideInAnyPackage("com.chandler.learning.agent.controller..",
                    "com.chandler.learning.agent.service..", "com.chandler.learning.agent.mapper..");

    @ArchTest
    static final ArchRule BUSINESS_DOMAINS_DO_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
            .that().resideInAnyPackage("..identity.domain..", "..vocabulary.domain..", "..learning.domain..",
                    "..reading.domain..", "..task.domain..", "..system.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule APPLICATION_DO_NOT_DEPEND_ON_CONTROLLERS = noClasses()
            .that().resideInAnyPackage("..identity.application..", "..vocabulary.application..",
                    "..learning.application..", "..reading.application..", "..task.application..",
                    "..system.application..", "..application..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule IDENTITY_APPLICATION_USES_ONLY_OWN_INFRASTRUCTURE = noClasses()
            .that().resideInAPackage("..identity.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..vocabulary.infrastructure..",
                    "..learning.infrastructure..", "..reading.infrastructure..",
                    "..task.infrastructure..", "..system.infrastructure..");

    @ArchTest
    static final ArchRule VOCABULARY_APPLICATION_USES_ONLY_OWN_INFRASTRUCTURE = noClasses()
            .that().resideInAPackage("..vocabulary.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..identity.infrastructure..",
                    "..learning.infrastructure..", "..reading.infrastructure..",
                    "..task.infrastructure..", "..system.infrastructure..");

    @ArchTest
    static final ArchRule LEARNING_APPLICATION_USES_ONLY_OWN_INFRASTRUCTURE = noClasses()
            .that().resideInAPackage("..learning.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..identity.infrastructure..",
                    "..vocabulary.infrastructure..", "..reading.infrastructure..",
                    "..task.infrastructure..", "..system.infrastructure..");

    @ArchTest
    static final ArchRule READING_APPLICATION_USES_ONLY_OWN_INFRASTRUCTURE = noClasses()
            .that().resideInAPackage("..reading.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..identity.infrastructure..",
                    "..vocabulary.infrastructure..", "..learning.infrastructure..",
                    "..task.infrastructure..", "..system.infrastructure..");

    @ArchTest
    static final ArchRule TASK_APPLICATION_USES_ONLY_OWN_INFRASTRUCTURE = noClasses()
            .that().resideInAPackage("..task.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..identity.infrastructure..",
                    "..vocabulary.infrastructure..", "..learning.infrastructure..",
                    "..reading.infrastructure..", "..system.infrastructure..");

    @ArchTest
    static final ArchRule SYSTEM_APPLICATION_USES_ONLY_OWN_INFRASTRUCTURE = noClasses()
            .that().resideInAPackage("..system.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..identity.infrastructure..",
                    "..vocabulary.infrastructure..", "..learning.infrastructure..",
                    "..reading.infrastructure..", "..task.infrastructure..");
}
