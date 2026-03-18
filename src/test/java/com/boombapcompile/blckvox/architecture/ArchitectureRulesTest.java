package com.boombapcompile.blckvox.architecture;

import com.boombapcompile.blckvox.service.stt.AbstractSttEngine;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.util.TranscriptionGuard;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureRulesTest {

    private static final String BASE = "com.boombapcompile.blckvox";

    private static JavaClasses appClasses;

    @BeforeAll
    static void importClasses() {
        appClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Nested
    @DisplayName("Domain & Exception Purity")
    class DomainAndExceptionPurity {

        @Test
        @DisplayName("Domain should not depend on service or config")
        void domainShouldNotDependOnOtherLayers() {
            noClasses()
                    .that().resideInAPackage(BASE + ".domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            BASE + ".service..",
                            BASE + ".config..")
                    .check(appClasses);
        }

        @Test
        @DisplayName("Exception should not depend on service or config")
        void exceptionShouldNotDependOnOtherLayers() {
            noClasses()
                    .that().resideInAPackage(BASE + ".exception..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            BASE + ".service..",
                            BASE + ".config..")
                    .check(appClasses);
        }

        @Test
        @DisplayName("Domain and exception classes should not use Spring stereotypes")
        void noSpringStereotypesInDomainOrException() {
            noClasses()
                    .that().resideInAnyPackage(BASE + ".domain..", BASE + ".exception..")
                    .should().beAnnotatedWith("org.springframework.stereotype.Component")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Service")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
                    .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .orShould().beAnnotatedWith("org.springframework.context.annotation.Configuration")
                    .check(appClasses);
        }
    }

    @Nested
    @DisplayName("STT Engine Isolation")
    class SttEngineIsolation {

        @Test
        @DisplayName("STT packages should not depend on unrelated service packages")
        void sttShouldNotDependOnUnrelatedPackages() {
            noClasses()
                    .that().resideInAPackage(BASE + ".service.stt..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            BASE + ".service.hotkey..",
                            BASE + ".service.fallback..",
                            BASE + ".service.tray..",
                            BASE + ".service.livecaption..")
                    .check(appClasses);
        }

        @Test
        @DisplayName("Vosk should not depend on Whisper")
        void voskShouldNotDependOnWhisper() {
            noClasses()
                    .that().resideInAPackage(BASE + ".service.stt.vosk..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(BASE + ".service.stt.whisper..")
                    .check(appClasses);
        }

        @Test
        @DisplayName("Whisper should not depend on Vosk")
        void whisperShouldNotDependOnVosk() {
            noClasses()
                    .that().resideInAPackage(BASE + ".service.stt.whisper..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(BASE + ".service.stt.vosk..")
                    .check(appClasses);
        }
    }

    @Nested
    @DisplayName("STT Engine Conventions")
    class SttEngineConventions {

        @Test
        @DisplayName("Concrete SttEngine implementations should extend AbstractSttEngine")
        void concreteSttEnginesShouldExtendAbstractBase() {
            classes()
                    .that().implement(SttEngine.class)
                    .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                    .should().beAssignableTo(AbstractSttEngine.class)
                    .check(appClasses);
        }

        @Test
        @DisplayName("AbstractSttEngine should use TranscriptionGuard")
        void abstractSttEngineShouldUseTranscriptionGuard() {
            classes()
                    .that().haveSimpleName("AbstractSttEngine")
                    .should().dependOnClassesThat().areAssignableTo(TranscriptionGuard.class)
                    .check(appClasses);
        }
    }

    @Nested
    @DisplayName("Hotkey & Fallback Isolation")
    class HotkeyAndFallbackIsolation {

        @Test
        @DisplayName("Hotkey should not depend on STT, audio, orchestration, or fallback")
        void hotkeyShouldBeIsolated() {
            noClasses()
                    .that().resideInAPackage(BASE + ".service.hotkey..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            BASE + ".service.stt..",
                            BASE + ".service.audio..",
                            BASE + ".service.orchestration..",
                            BASE + ".service.fallback..")
                    .check(appClasses);
        }

        @Test
        @DisplayName("Fallback should not depend on STT, audio, or hotkey")
        void fallbackShouldBeIsolated() {
            noClasses()
                    .that().resideInAPackage(BASE + ".service.fallback..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            BASE + ".service.stt..",
                            BASE + ".service.audio..",
                            BASE + ".service.hotkey..")
                    .check(appClasses);
        }
    }

    @Nested
    @DisplayName("Config Boundaries")
    class ConfigBoundaries {

        @Test
        @DisplayName("@ConfigurationProperties should reside in config package")
        void configurationPropertiesShouldResideInConfig() {
            classes()
                    .that().areAnnotatedWith(
                            "org.springframework.boot.context.properties.ConfigurationProperties")
                    .should().resideInAPackage(BASE + ".config..")
                    .check(appClasses);
        }

        @Test
        @DisplayName("@Configuration should reside in config package")
        void configurationShouldResideInConfig() {
            classes()
                    .that().areAnnotatedWith(
                            "org.springframework.context.annotation.Configuration")
                    .should().resideInAPackage(BASE + ".config..")
                    .check(appClasses);
        }
    }

    @Nested
    @DisplayName("Spring Conventions")
    class SpringConventions {

        @Test
        @DisplayName("No field injection with @Autowired")
        void noAutowiredFieldInjection() {
            noFields()
                    .should().beAnnotatedWith(
                            "org.springframework.beans.factory.annotation.Autowired")
                    .check(appClasses);
        }

        @Test
        @DisplayName("No field injection with @Inject")
        void noInjectFieldInjection() {
            noFields()
                    .should().beAnnotatedWith("jakarta.inject.Inject")
                    .check(appClasses);
        }

        @Test
        @DisplayName("No field injection with @Resource")
        void noResourceFieldInjection() {
            noFields()
                    .should().beAnnotatedWith("jakarta.annotation.Resource")
                    .check(appClasses);
        }
    }

    @Nested
    @DisplayName("Naming Conventions")
    class NamingConventions {

        @Test
        @DisplayName("Exception classes should end with 'Exception'")
        void exceptionClassesShouldEndWithException() {
            classes()
                    .that().areAssignableTo(Exception.class)
                    .should().haveSimpleNameEndingWith("Exception")
                    .check(appClasses);
        }

        @Test
        @DisplayName("No classes should use 'Impl' suffix")
        void noImplSuffix() {
            noClasses()
                    .should().haveSimpleNameEndingWith("Impl")
                    .check(appClasses);
        }
    }

    @Nested
    @DisplayName("Layered Architecture")
    class LayeredArchitectureRules {

        @Test
        @DisplayName("Layered architecture: Service → Domain, Config ↔ Service")
        void layeredArchitectureShouldBeRespected() {
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Service").definedBy(BASE + ".service..")
                    .layer("Domain").definedBy(BASE + ".domain..")
                    .layer("Config").definedBy(BASE + ".config..")
                    .whereLayer("Service").mayOnlyAccessLayers("Domain", "Config")
                    .whereLayer("Domain").mayNotAccessAnyLayer()
                    .whereLayer("Config").mayOnlyAccessLayers("Service", "Domain")
                    .check(appClasses);
        }

        @Test
        @DisplayName("Services should not depend on HTTP/Servlet types")
        void servicesShouldNotDependOnWeb() {
            noClasses()
                    .that().resideInAPackage(BASE + ".service..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("jakarta.servlet..", "org.springframework.web..")
                    .check(appClasses);
        }
    }

    @Nested
    @DisplayName("Cycle Prevention")
    class CyclePrevention {

        @Test
        @DisplayName("Top-level packages should be free of cycles")
        void topLevelPackagesShouldBeCycleFree() {
            // config↔service is an inherent Spring Boot pattern: @Configuration classes
            // wire service beans (config→service) and services inject @ConfigurationProperties
            // (service→config). Other top-level cycles are prohibited.
            slices()
                    .matching(BASE + ".(*)..")
                    .should().beFreeOfCycles()
                    .ignoreDependency(
                            resideInAPackage(BASE + ".config.."),
                            resideInAPackage(BASE + ".service.."))
                    .ignoreDependency(
                            resideInAPackage(BASE + ".service.."),
                            resideInAPackage(BASE + ".config.."))
                    .check(appClasses);
        }
    }
}
