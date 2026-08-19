package com.chandler.learning.agent.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** 非 AI 代码与 AI 代码共同遵守的基础依赖方向。 */
@AnalyzeClasses(packages = "com.chandler.learning.agent")
class LayeredPackageArchitectureTest {

    @ArchTest
    static final ArchRule CONTROLLERS_DO_NOT_ACCESS_PERSISTENCE = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().resideInAnyPackage("..mapper..", "..infrastructure..");

    @ArchTest
    static final ArchRule DOMAIN_ENTITIES_DO_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
            .that().resideInAPackage("..domain.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..controller..", "..service..", "..mapper..", "..api..", "..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule SERVICES_DO_NOT_DEPEND_ON_CONTROLLERS = noClasses()
            .that().resideInAnyPackage("..service..", "..application..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");
}
