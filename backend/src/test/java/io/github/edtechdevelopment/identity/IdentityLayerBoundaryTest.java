package io.github.edtechdevelopment.identity;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class IdentityLayerBoundaryTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.edtechdevelopment.identity.api",
                    "io.github.edtechdevelopment.identity.presentation.auth");

    @Test
    void publicApiDoesNotDependOnIdentityInternals() {
        noClasses().that().resideInAPackage("io.github.edtechdevelopment.identity.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.edtechdevelopment.identity.application..",
                        "io.github.edtechdevelopment.identity.domain..",
                        "io.github.edtechdevelopment.identity.infrastructure..",
                        "io.github.edtechdevelopment.identity.presentation..")
                .check(classes);
    }

    @Test
    void authPresentationUsesApplicationRegistrationTypes() {
        noClasses().that().resideInAPackage("io.github.edtechdevelopment.identity.presentation.auth..")
                .should().dependOnClassesThat().resideInAPackage(
                        "io.github.edtechdevelopment.identity.api.command.registration..")
                .check(classes);
    }
}
