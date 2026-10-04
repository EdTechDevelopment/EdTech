package io.github.edtechdevelopment.identity.application.service.account;

import io.github.edtechdevelopment.identity.api.IdentityRegistrationGateway;
import io.github.edtechdevelopment.identity.api.IdentityRoleGateway;
import io.github.edtechdevelopment.identity.api.command.registration.RegistrationData;
import io.github.edtechdevelopment.identity.api.command.role.AddUserRoleCommand;
import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.identity.api.query.IdentityQuery;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUserEmails.IDENTITY_USER_EMAILS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUserRoles.IDENTITY_USER_ROLES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IdentityWorkflowCommandsIntegrationTest {

    private final IdentityRegistrationGateway registration;
    private final IdentityRoleGateway roles;
    private final IdentityQuery identityQuery;
    private final UserRepository users;
    private final TransactionTemplate transactions;
    private final DSLContext dslContext;

    @Autowired
    IdentityWorkflowCommandsIntegrationTest(
            IdentityRegistrationGateway registration,
            IdentityRoleGateway roles,
            IdentityQuery identityQuery,
            UserRepository users,
            PlatformTransactionManager transactionManager,
            DSLContext dslContext
    ) {
        this.registration = registration;
        this.roles = roles;
        this.identityQuery = identityQuery;
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
        this.dslContext = dslContext;
    }

    @Test
    void roleAdditionRequiresWorkflowTransaction() {
        assertThrows(IllegalTransactionStateException.class, () -> roles.addRole(
                new AddUserRoleCommand(UUID.randomUUID(), UserRoleView.TEACHER)
        ));
    }

    @Test
    void registrationJoinsWorkflowTransaction() {
        RegistrationData command = registrationCommand();

        transactions.executeWithoutResult(status -> {
            var result = registration.register(command);

            assertEquals(1, dslContext.fetchCount(IDENTITY_USER_EMAILS,
                    IDENTITY_USER_EMAILS.EMAIL.eq(result.email())));
            assertEquals(result.userId(), users.findByIdForUpdate(result.userId()).orElseThrow().id());
            status.setRollbackOnly();
        });

        assertEquals(0, dslContext.fetchCount(IDENTITY_USER_EMAILS,
                IDENTITY_USER_EMAILS.EMAIL.eq(command.email())));
    }

    @Test
    void failedWorkflowRollsBackUser() {
        RegistrationData command = registrationCommand();

        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            registration.register(command);
            throw new IllegalStateException("Tutoring profile creation failed");
        }));

        assertEquals(0, dslContext.fetchCount(IDENTITY_USER_EMAILS,
                IDENTITY_USER_EMAILS.EMAIL.eq(command.email())));
    }

    @Test
    void secondRoleAndTrustedQueriesSeeSameAccount() {
        RegistrationData command = registrationCommand();

        transactions.executeWithoutResult(status -> {
            var result = registration.register(command);
            var user = users.findByIdForUpdate(result.userId()).orElseThrow();
            user.verifyRegistrationEmail(new Email(result.email()), user.createdAt());
            users.save(user);

            roles.addRole(new AddUserRoleCommand(result.userId(), UserRoleView.TEACHER));

            assertEquals(2, dslContext.fetchCount(IDENTITY_USER_ROLES,
                    IDENTITY_USER_ROLES.USER_ID.eq(result.userId())));
            assertEquals(Set.of(result.userId()), identityQuery.findActiveUserIds(Set.of(result.userId())));
            assertEquals(26, identityQuery.findAgesByIds(Set.of(result.userId()), LocalDate.of(2026, 10, 3))
                    .get(result.userId()));
            assertEquals(result.userId(), identityQuery.findUserByVerifiedEmail(result.email())
                    .orElseThrow().id());
            status.setRollbackOnly();
        });
    }

    private static RegistrationData registrationCommand() {
        return new RegistrationData(
                "workflow-" + UUID.randomUUID() + "@example.test",
                "Strong!42",
                "Anna",
                "Petrova",
                LocalDate.of(2000, 1, 1),
                Set.of(UserRoleView.STUDENT)
        );
    }
}
