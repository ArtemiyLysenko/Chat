package edu.artemiy.chat.app.bootstrap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import edu.artemiy.chat.testing.ArchitecturePackages;

class ApplicationArchitectureTests {

    private final com.tngtech.archunit.core.domain.JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ArchitecturePackages.BASE_PACKAGE);

    @Test
    void appModuleOnlyContainsBootstrapAndInboundAdapterPackages() {
        classes()
            .that().resideInAPackage("edu.artemiy.chat.app..")
            .should().resideInAnyPackage(ArchitecturePackages.appAllowedPackages().toArray(String[]::new))
            .check(classes);
    }

    @Test
    void featureModulesDoNotDependOnAdapters() {
        noClasses()
            .that().resideInAnyPackage(
                "edu.artemiy.chat.identity..",
                "edu.artemiy.chat.rooms..",
                "edu.artemiy.chat.contacts..",
                "edu.artemiy.chat.messaging..",
                "edu.artemiy.chat.attachments..",
                "edu.artemiy.chat.presence..",
                "edu.artemiy.chat.federation..",
                "edu.artemiy.chat.admin.."
            )
            .should().dependOnClassesThat().resideInAPackage("edu.artemiy.chat.adapters..")
            .check(classes);
    }

    @Test
    void adaptersOnlyUseFeatureApisAndSpis() {
        noClasses()
            .that().resideInAPackage("edu.artemiy.chat.adapters..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "edu.artemiy.chat.identity.domain..",
                "edu.artemiy.chat.identity.application..",
                "edu.artemiy.chat.identity.internal..",
                "edu.artemiy.chat.rooms.domain..",
                "edu.artemiy.chat.rooms.application..",
                "edu.artemiy.chat.rooms.internal..",
                "edu.artemiy.chat.contacts.domain..",
                "edu.artemiy.chat.contacts.application..",
                "edu.artemiy.chat.contacts.internal..",
                "edu.artemiy.chat.messaging.domain..",
                "edu.artemiy.chat.messaging.application..",
                "edu.artemiy.chat.messaging.internal..",
                "edu.artemiy.chat.attachments.domain..",
                "edu.artemiy.chat.attachments.application..",
                "edu.artemiy.chat.attachments.internal..",
                "edu.artemiy.chat.presence.domain..",
                "edu.artemiy.chat.presence.application..",
                "edu.artemiy.chat.presence.internal..",
                "edu.artemiy.chat.federation.domain..",
                "edu.artemiy.chat.federation.application..",
                "edu.artemiy.chat.federation.internal..",
                "edu.artemiy.chat.admin.domain..",
                "edu.artemiy.chat.admin.application..",
                "edu.artemiy.chat.admin.internal.."
            )
            .check(classes);
    }

    @Test
    void appModuleOnlyDependsOnFeatureApisAndAdapters() {
        noClasses()
            .that().resideInAPackage("edu.artemiy.chat.app..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "edu.artemiy.chat.identity.domain..",
                "edu.artemiy.chat.identity.application..",
                "edu.artemiy.chat.identity.spi..",
                "edu.artemiy.chat.identity.internal..",
                "edu.artemiy.chat.rooms.domain..",
                "edu.artemiy.chat.rooms.application..",
                "edu.artemiy.chat.rooms.spi..",
                "edu.artemiy.chat.rooms.internal..",
                "edu.artemiy.chat.contacts.domain..",
                "edu.artemiy.chat.contacts.application..",
                "edu.artemiy.chat.contacts.spi..",
                "edu.artemiy.chat.contacts.internal..",
                "edu.artemiy.chat.messaging.domain..",
                "edu.artemiy.chat.messaging.application..",
                "edu.artemiy.chat.messaging.spi..",
                "edu.artemiy.chat.messaging.internal..",
                "edu.artemiy.chat.attachments.domain..",
                "edu.artemiy.chat.attachments.application..",
                "edu.artemiy.chat.attachments.internal..",
                "edu.artemiy.chat.presence.domain..",
                "edu.artemiy.chat.presence.application..",
                "edu.artemiy.chat.presence.spi..",
                "edu.artemiy.chat.presence.internal..",
                "edu.artemiy.chat.federation.domain..",
                "edu.artemiy.chat.federation.application..",
                "edu.artemiy.chat.federation.spi..",
                "edu.artemiy.chat.federation.internal..",
                "edu.artemiy.chat.admin.domain..",
                "edu.artemiy.chat.admin.application..",
                "edu.artemiy.chat.admin.spi..",
                "edu.artemiy.chat.admin.internal.."
            )
            .check(classes);
    }

    @Test
    void crossFeatureAccessGoesThroughApiOnly() {
        List<String> features = ArchitecturePackages.FEATURES;
        for (String feature : features) {
            List<String> otherFeatureRoots = new ArrayList<>();
            for (String candidate : features) {
                if (!candidate.equals(feature)) {
                    otherFeatureRoots.add(ArchitecturePackages.featureRoot(candidate));
                }
            }
            ArchRule rule = noClasses()
                .that().resideInAnyPackage(otherFeatureRoots.toArray(String[]::new))
                .should().dependOnClassesThat()
                .resideInAnyPackage(ArchitecturePackages.nonApiPackages(feature).toArray(String[]::new));
            assertThatNoException().isThrownBy(() -> rule.check(classes));
        }
    }
}
