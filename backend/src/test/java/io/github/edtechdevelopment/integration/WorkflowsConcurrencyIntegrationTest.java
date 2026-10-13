package io.github.edtechdevelopment.integration;

import io.github.edtechdevelopment.identity.api.exception.IdentityCommandException;
import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.tutoring.api.command.profile.TutoringProfileCreationCommands;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.StudentProfileData;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;
import io.github.edtechdevelopment.workflows.application.exception.WorkflowException;
import io.github.edtechdevelopment.workflows.application.me.model.MeResult;
import io.github.edtechdevelopment.workflows.application.onboarding.model.AddTeacherRoleCommand;
import io.github.edtechdevelopment.workflows.application.onboarding.port.in.AddTeacherRoleUseCase;
import io.github.edtechdevelopment.workflows.application.registration.model.RegistrationCommand;
import io.github.edtechdevelopment.workflows.application.registration.model.RegistrationOutcome;
import io.github.edtechdevelopment.workflows.application.registration.port.in.RegisterAccountUseCase;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.Tables.*;
import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.Tables.NOTIFICATION_EMAIL_DELIVERIES;
import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.*;
import static io.github.edtechdevelopment.workflows.infrastructure.persistence.data.generated.Tables.WORKFLOWS_OPERATIONS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class WorkflowsConcurrencyIntegrationTest {

    @Autowired RegisterAccountUseCase register;
    @Autowired AddTeacherRoleUseCase addTeacher;
    @Autowired UserRepository users;
    @Autowired Clock clock;
    @Autowired DSLContext dsl;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean TutoringProfileCreationCommands profiles;
    private final Set<String> emails = new HashSet<>();
    private final Set<UUID> keys = new HashSet<>();
    private TransactionTemplate transactions;

    @BeforeEach
    void prepare() {
        transactions = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanOwnFixtures() {
        transactions.executeWithoutResult(status -> {
            Set<UUID> ids = dsl.select(IDENTITY_USER_EMAILS.USER_ID).from(IDENTITY_USER_EMAILS)
                    .where(IDENTITY_USER_EMAILS.EMAIL.in(emails)).fetchSet(IDENTITY_USER_EMAILS.USER_ID);
            dsl.deleteFrom(WORKFLOWS_OPERATIONS).where(WORKFLOWS_OPERATIONS.OPERATION_ID.in(keys)).execute();
            dsl.deleteFrom(TUTORING_TEACHER_PROFILES).where(TUTORING_TEACHER_PROFILES.USER_ID.in(ids)).execute();
            dsl.deleteFrom(TUTORING_STUDENT_PROFILES).where(TUTORING_STUDENT_PROFILES.USER_ID.in(ids)).execute();
            dsl.deleteFrom(NOTIFICATION_EMAIL_DELIVERIES).where(NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.in(emails)).execute();
            dsl.deleteFrom(IDENTITY_USERS).where(IDENTITY_USERS.ID.in(ids)).execute();
        });
    }

    @Test
    void concurrentRegistrationWithOneKeyCreatesOneAccountProfileAndLetter() throws Exception {
        RegistrationCommand command = registration(false);
        List<RegistrationOutcome> results = together(() -> register.register(command), () -> register.register(command));
        assertThat(results.getFirst()).isEqualTo(results.getLast());
        assertThat(dsl.fetchCount(IDENTITY_USER_EMAILS, IDENTITY_USER_EMAILS.EMAIL.eq(command.email()))).isEqualTo(1);
        UUID id = userId(command.email());
        assertThat(dsl.fetchCount(TUTORING_STUDENT_PROFILES, TUTORING_STUDENT_PROFILES.USER_ID.eq(id))).isEqualTo(1);
        assertThat(dsl.fetchCount(NOTIFICATION_EMAIL_DELIVERIES, NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(command.email()))).isEqualTo(1);
        assertThat(dsl.fetchCount(WORKFLOWS_OPERATIONS, WORKFLOWS_OPERATIONS.OPERATION_ID.eq(command.operationId()))).isEqualTo(1);
    }

    @Test
    void concurrentOnboardingWithOneKeyReturnsTheSameResult() throws Exception {
        UUID id = activeStudent();
        AddTeacherRoleCommand command = new AddTeacherRoleCommand(key(), id, teacher());
        List<MeResult> results = together(() -> addTeacher.addTeacherRole(command), () -> addTeacher.addTeacherRole(command));
        assertThat(results.getFirst()).isEqualTo(results.getLast());
        assertThat(dsl.fetchCount(TUTORING_TEACHER_PROFILES, TUTORING_TEACHER_PROFILES.USER_ID.eq(id))).isEqualTo(1);
        assertThat(dsl.fetchCount(WORKFLOWS_OPERATIONS, WORKFLOWS_OPERATIONS.OPERATION_ID.eq(command.operationId()))).isEqualTo(1);
    }

    @Test
    void competingDifferentKeysForOneRoleProduceOneSuccessAndOneConflict() throws Exception {
        UUID id = activeStudent();
        AddTeacherRoleCommand first = new AddTeacherRoleCommand(key(), id, teacher());
        AddTeacherRoleCommand second = new AddTeacherRoleCommand(key(), id, teacher());
        List<String> outcomes = together(() -> onboardingOutcome(first), () -> onboardingOutcome(second));
        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "CONFLICT");
        assertThat(dsl.fetchCount(TUTORING_TEACHER_PROFILES, TUTORING_TEACHER_PROFILES.USER_ID.eq(id))).isEqualTo(1);
        assertThat(dsl.fetchCount(IDENTITY_USER_ROLES, IDENTITY_USER_ROLES.USER_ID.eq(id))).isEqualTo(2);
        assertThat(dsl.fetchCount(WORKFLOWS_OPERATIONS,
                WORKFLOWS_OPERATIONS.OPERATION_TYPE.eq("ADD_TEACHER_ROLE").and(WORKFLOWS_OPERATIONS.CONTEXT_KEY.eq(id.toString())))).isEqualTo(1);
    }

    @Test
    void failureAfterBothProfilesWereInsertedRollsBackTheWholeRegistration() {
        RegistrationCommand command = registration(true);
        transactions.executeWithoutResult(status -> doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Simulated failure after profile insertion");
        }).when(profiles).createInitialProfiles(any()));
        assertThatThrownBy(() -> register.register(command)).isInstanceOf(IllegalStateException.class);
        assertThat(dsl.fetchCount(IDENTITY_USER_EMAILS, IDENTITY_USER_EMAILS.EMAIL.eq(command.email()))).isZero();
        assertThat(dsl.fetchCount(NOTIFICATION_EMAIL_DELIVERIES, NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(command.email()))).isZero();
        assertThat(dsl.fetchCount(WORKFLOWS_OPERATIONS, WORKFLOWS_OPERATIONS.OPERATION_ID.eq(command.operationId()))).isZero();
        assertThat(dsl.fetchCount(TUTORING_TEACHER_PROFILES, TUTORING_TEACHER_PROFILES.DISPLAY_NAME.eq(command.email()))).isZero();
        assertThat(dsl.fetchCount(TUTORING_STUDENT_PROFILES, TUTORING_STUDENT_PROFILES.DISPLAY_NAME.eq(command.email()))).isZero();
    }

    private String onboardingOutcome(AddTeacherRoleCommand command) {
        try {
            addTeacher.addTeacherRole(command);
            return "SUCCESS";
        } catch (WorkflowException exception) {
            assertThat(exception.reason()).isEqualTo(WorkflowException.Reason.ROLE_ALREADY_ENABLED);
            return "CONFLICT";
        } catch (IdentityCommandException exception) {
            assertThat(exception.reason()).isEqualTo(IdentityCommandException.Reason.ACCOUNT_STATE_CONFLICT);
            return "CONFLICT";
        }
    }

    private UUID activeStudent() {
        RegistrationCommand command = registration(false);
        register.register(command);
        return transactions.execute(status -> {
            var user = users.findByEmail(new Email(command.email())).orElseThrow();
            user.verifyRegistrationEmail(user.email(), clock.instant());
            users.save(user);
            return user.id();
        });
    }

    private RegistrationCommand registration(boolean both) {
        String email = "workflows-concurrency-" + UUID.randomUUID() + "@example.test";
        emails.add(email);
        Set<UserRoleView> roles = both ? Set.of(UserRoleView.STUDENT, UserRoleView.TEACHER) : Set.of(UserRoleView.STUDENT);
        TeacherProfileData teacher = both ? new TeacherProfileData(email, "contact@example.test", List.of(), Set.of("MATH"),
                null, null, null, null, null) : null;
        StudentProfileData student = new StudentProfileData(email, "contact@example.test", List.of(), Set.of(), null);
        return new RegistrationCommand(key(), email, "StrongPassword42!", "Test", "User",
                LocalDate.of(2000, 1, 1), roles, teacher, student);
    }

    private TeacherProfileData teacher() {
        return new TeacherProfileData("Teacher", "contact@example.test", List.of(), Set.of("MATH"), null, null, null, null, null);
    }

    private UUID userId(String email) {
        return dsl.select(IDENTITY_USER_EMAILS.USER_ID).from(IDENTITY_USER_EMAILS)
                .where(IDENTITY_USER_EMAILS.EMAIL.eq(email)).fetchSingle().value1();
    }

    private UUID key() {
        UUID key = UUID.randomUUID();
        keys.add(key);
        return key;
    }

    private <T> List<T> together(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<T> a = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent test did not start");
                }
                return first.call();
            });
            Future<T> b = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent test did not start");
                }
                return second.call();
            });
            start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }
}

