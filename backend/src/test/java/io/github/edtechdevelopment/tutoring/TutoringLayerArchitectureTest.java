package io.github.edtechdevelopment.tutoring;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class TutoringLayerArchitectureTest {

    private static final String BASE = "io.github.edtechdevelopment.tutoring.";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(BASE.substring(0, BASE.length() - 1));

    @Test
    void domainDoesNotDependOnApplicationApiStorageOrFrameworks() {
        noClasses().that().resideInAPackage(BASE + "domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "application..", BASE + "api..", BASE + "infrastructure..",
                        BASE + "presentation..", "org.springframework..", "org.jooq..", "jakarta..")
                .check(CLASSES);
    }

    @Test
    void apiDoesNotExposeInternalModuleTypes() {
        noClasses().that().resideInAPackage(BASE + "api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "domain..", BASE + "application..", BASE + "infrastructure..",
                        BASE + "presentation..", "org.jooq..", "jakarta.servlet..")
                .check(CLASSES);
    }

    @Test
    void applicationDoesNotDependOnTransportOrStorageImplementations() {
        noClasses().that().resideInAPackage(BASE + "application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "presentation..", BASE + "infrastructure..", "org.jooq..", "jakarta.servlet..")
                .check(CLASSES);
    }

    @Test
    void presentationUsesApplicationWithoutDomainRepositoriesOrTrustedApiCommands() {
        noClasses().that().resideInAPackage(BASE + "presentation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "domain..", BASE + "infrastructure..",
                        BASE + "api.command..", BASE + "api.query..",
                        BASE + "application..port.out..", "org.jooq..")
                .check(CLASSES);
    }
}

