package com.chandler.learning.agent.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** AI 领域包边界，防止业务代码重新散落到根包或跨域直连 Mapper。 */
@AnalyzeClasses(packages = "com.chandler.learning.agent")
class AiPackageArchitectureTest {

    @ArchTest
    static final ArchRule AI_CONTROLLERS_STAY_IN_API = classes()
            .that().areAnnotatedWith(RestController.class)
            .and().resideInAPackage("..ai..")
            .should().resideInAPackage("..api.controller..");

    @ArchTest
    static final ArchRule API_DOES_NOT_ACCESS_MAPPERS = noClasses()
            .that().resideInAPackage("..ai..api..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..mapper..");

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
            .that().resideInAPackage("..ai..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..ai..api..", "..ai..infrastructure..");

    @ArchTest
    static final ArchRule AGENT_APPLICATION_DOES_NOT_ACCESS_OTHER_MAPPERS = noClasses()
            .that().resideInAPackage("..ai.agent.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..ai.model.infrastructure..", "..ai.chat.infrastructure..", "..ai.prompt.infrastructure..");

    @ArchTest
    static final ArchRule MODEL_APPLICATION_DOES_NOT_ACCESS_OTHER_MAPPERS = noClasses()
            .that().resideInAPackage("..ai.model.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..ai.agent.infrastructure..", "..ai.chat.infrastructure..", "..ai.prompt.infrastructure..");

    @ArchTest
    static final ArchRule CHAT_APPLICATION_DOES_NOT_ACCESS_OTHER_MAPPERS = noClasses()
            .that().resideInAPackage("..ai.chat.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..ai.agent.infrastructure..", "..ai.model.infrastructure..", "..ai.prompt.infrastructure..");

    @ArchTest
    static final ArchRule PROMPT_APPLICATION_DOES_NOT_ACCESS_OTHER_MAPPERS = noClasses()
            .that().resideInAPackage("..ai.prompt.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..ai.agent.infrastructure..", "..ai.model.infrastructure..", "..ai.chat.infrastructure..");

    @ArchTest
    static final ArchRule MODEL_CLIENTS_STAY_IN_GATEWAY = classes()
            .that().haveSimpleNameEndingWith("ModelClient")
            .and().resideInAPackage("..ai..")
            .should().resideInAPackage("..ai.gateway.client..");

    @ArchTest
    static final ArchRule REQUEST_ADAPTERS_STAY_IN_GATEWAY = classes()
            .that().haveSimpleNameEndingWith("RequestAdapter")
            .and().resideInAPackage("..ai..")
            .should().resideInAPackage("..ai.gateway.adapter..");

    @ArchTest
    static final ArchRule RESPONSE_PARSERS_STAY_IN_GATEWAY = classes()
            .that().haveSimpleNameEndingWith("ResponseParser")
            .and().resideInAPackage("..ai..")
            .should().resideInAPackage("..ai.gateway.parser..");
}
