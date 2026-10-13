package io.github.edtechdevelopment.workflows;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.edtechdevelopment.identity.api.model.OwnAccountView;
import io.github.edtechdevelopment.identity.api.query.OwnAccountQuery;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class WorkflowsArchitectureTest {

    private static final String BASE = "io.github.edtechdevelopment.";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests()).importPackages(BASE.substring(0, BASE.length() - 1));

    @Test
    void applicationUsesOnlyPublicContractsOfOtherModules() {
        noClasses().that().resideInAPackage(BASE + "workflows.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "identity.application..", BASE + "identity.domain..", BASE + "identity.infrastructure..", BASE + "identity.presentation..",
                        BASE + "tutoring.application..", BASE + "tutoring.domain..", BASE + "tutoring.infrastructure..", BASE + "tutoring.presentation..",
                        BASE + "notifications.application..", BASE + "notifications.domain..", BASE + "notifications.infrastructure..",
                        BASE + "workflows.infrastructure..", BASE + "workflows.presentation..",
                        "org.jooq..", "tools.jackson..", "jakarta.servlet..", "javax.crypto..")
                .check(CLASSES);
    }

    @Test
    void infrastructureDoesNotReadOtherModulesRepositoriesOrTables() {
        noClasses().that().resideInAPackage(BASE + "workflows.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "identity.infrastructure..", BASE + "identity.application..", BASE + "identity.domain..",
                        BASE + "tutoring.infrastructure..", BASE + "tutoring.application..", BASE + "tutoring.domain..",
                        BASE + "notifications.infrastructure..", BASE + "notifications.application..", BASE + "notifications.domain..")
                .check(CLASSES);
    }

    @Test
    void presentationCallsApplicationAndDoesNotUseTrustedGateways() {
        noClasses().that().resideInAPackage(BASE + "workflows.presentation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "workflows.infrastructure..", BASE + "workflows.application..port.out..",
                        BASE + "identity.infrastructure..", BASE + "identity.application..", BASE + "identity.domain..", BASE + "identity.presentation..",
                        BASE + "tutoring.infrastructure..", BASE + "tutoring.application..", BASE + "tutoring.domain..", BASE + "tutoring.presentation..",
                        BASE + "identity.api.command..", BASE + "identity.api.query..", BASE + "tutoring.api.command..", BASE + "tutoring.api.query..", "org.jooq..")
                .check(CLASSES);
    }

    @Test
    void privateAccountDataIsNotUsedByTutoringSchedulingOrNotifications() {
        noClasses().that().resideInAnyPackage(BASE + "tutoring..", BASE + "scheduling..", BASE + "notifications..")
                .should().dependOnClassesThat().haveFullyQualifiedName(OwnAccountView.class.getName()).check(CLASSES);
        noClasses().that().resideInAnyPackage(BASE + "tutoring..", BASE + "scheduling..", BASE + "notifications..")
                .should().dependOnClassesThat().haveFullyQualifiedName(OwnAccountQuery.class.getName()).check(CLASSES);
    }

    @Test
    void schedulingRemainsOutsideTheApprovedWorkflowsScope() {
        noClasses().that().resideInAPackage(BASE + "workflows..")
                .should().dependOnClassesThat().resideInAPackage(BASE + "scheduling..").check(CLASSES);
    }
}

