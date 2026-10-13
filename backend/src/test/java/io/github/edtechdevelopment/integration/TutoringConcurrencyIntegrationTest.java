package io.github.edtechdevelopment.integration;

import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.api.event.AccountEmailVerifiedEvent;
import io.github.edtechdevelopment.identity.api.model.AccountEmailVerificationPurpose;
import io.github.edtechdevelopment.identity.domain.user.model.*;
import io.github.edtechdevelopment.identity.infrastructure.messaging.event.AccountEmailEventOutbox;
import io.github.edtechdevelopment.identity.infrastructure.messaging.event.AccountEmailEventDispatcher;
import io.github.edtechdevelopment.notifications.api.InvitationNotificationGateway;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.in.HandleAccountEmailVerifiedUseCase;
import io.github.edtechdevelopment.tutoring.application.invitation.model.command.*;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.*;
import io.github.edtechdevelopment.tutoring.application.invitation.port.in.*;
import io.github.edtechdevelopment.tutoring.application.invitation.exception.InvitationResponseConflictException;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.*;
import io.github.edtechdevelopment.tutoring.domain.profile.model.*;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.*;
import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.Tables.NOTIFICATION_INVITATION_EMAIL_DELIVERIES;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.Tables.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class TutoringConcurrencyIntegrationTest {

    @Autowired DSLContext dsl;
    @Autowired Clock clock;
    @Autowired UserRepository users;
    @Autowired TeacherProfileRepository teachers;
    @Autowired StudentProfileRepository students;
    @Autowired CreateStudentInvitationUseCase create;
    @Autowired AcceptStudentInvitationUseCase accept;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired AccountEmailEventOutbox outbox;
    @Autowired AccountEmailEventDispatcher dispatcher;
    @MockitoSpyBean InvitationNotificationGateway notifications;
    @MockitoSpyBean HandleAccountEmailVerifiedUseCase verifiedEmails;

    private final Set<UUID> accountIds = new HashSet<>();
    private TransactionTemplate transactions;

    @BeforeEach
    void prepare() {
        transactions = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanOnlyThisTestsFixtures() {
        transactions.executeWithoutResult(status -> {
            var invitationIds = dsl.select(TUTORING_STUDENT_INVITATIONS.ID).from(TUTORING_STUDENT_INVITATIONS)
                    .where(TUTORING_STUDENT_INVITATIONS.TEACHER_USER_ID.in(accountIds));
            dsl.deleteFrom(NOTIFICATION_INVITATION_EMAIL_DELIVERIES)
                    .where(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.INVITATION_ID.in(invitationIds)).execute();
            dsl.deleteFrom(TUTORING_COMMAND_OPERATIONS).where(TUTORING_COMMAND_OPERATIONS.USER_ID.in(accountIds)).execute();
            dsl.deleteFrom(TUTORING_PROCESSED_IDENTITY_EVENTS).where(TUTORING_PROCESSED_IDENTITY_EVENTS.USER_ID.in(accountIds)).execute();
            dsl.deleteFrom(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX).where(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.USER_ID.in(accountIds)).execute();
            dsl.deleteFrom(TUTORING_TEACHER_STUDENTS).where(TUTORING_TEACHER_STUDENTS.TEACHER_USER_ID.in(accountIds)
                    .or(TUTORING_TEACHER_STUDENTS.STUDENT_USER_ID.in(accountIds))).execute();
            dsl.deleteFrom(TUTORING_STUDENT_INVITATIONS).where(TUTORING_STUDENT_INVITATIONS.TEACHER_USER_ID.in(accountIds)).execute();
            dsl.deleteFrom(TUTORING_TEACHER_PROFILES).where(TUTORING_TEACHER_PROFILES.USER_ID.in(accountIds)).execute();
            dsl.deleteFrom(TUTORING_STUDENT_PROFILES).where(TUTORING_STUDENT_PROFILES.USER_ID.in(accountIds)).execute();
            dsl.deleteFrom(IDENTITY_USERS).where(IDENTITY_USERS.ID.in(accountIds)).execute();
        });
    }

    @Test
    void serializesRequestsForAnOperationIdThatDoesNotYetExist() throws Exception {
        User teacher = account(UserRole.TEACHER, uniqueEmail());
        CreateStudentInvitationCommand command = new CreateStudentInvitationCommand(UUID.randomUUID(), teacher.id(), uniqueEmail());
        List<InvitationCreatedResult> results = concurrently(() -> create.execute(command));
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(dsl.fetchCount(TUTORING_COMMAND_OPERATIONS, TUTORING_COMMAND_OPERATIONS.OPERATION_ID.eq(command.operationId()))).isEqualTo(1);
        assertThat(dsl.fetchCount(TUTORING_STUDENT_INVITATIONS, TUTORING_STUDENT_INVITATIONS.ID.eq(results.getFirst().invitationId()))).isEqualTo(1);
        assertThat(dsl.fetchCount(NOTIFICATION_INVITATION_EMAIL_DELIVERIES,
                NOTIFICATION_INVITATION_EMAIL_DELIVERIES.INVITATION_ID.eq(results.getFirst().invitationId()))).isEqualTo(1);
    }

    @Test
    void concurrentAcceptanceCreatesOneRelationshipAndRejectsSecondOperation() throws Exception {
        User teacher = account(UserRole.TEACHER, uniqueEmail());
        User student = account(UserRole.STUDENT, uniqueEmail());
        InvitationCreatedResult invitation = create.execute(new CreateStudentInvitationCommand(UUID.randomUUID(), teacher.id(), student.email().value()));
        List<String> results = concurrently(() -> {
            try {
                accept.execute(new AcceptStudentInvitationCommand(UUID.randomUUID(), invitation.invitationId(), student.id()));
                return "ACCEPTED";
            } catch (InvitationResponseConflictException exception) {
                return exception.reason().name();
            }
        });
        assertThat(results).containsExactlyInAnyOrder("ACCEPTED", "ALREADY_RESPONDED");
        assertThat(dsl.fetchCount(TUTORING_TEACHER_STUDENTS, TUTORING_TEACHER_STUDENTS.TEACHER_USER_ID.eq(teacher.id()))).isEqualTo(1);
    }

    @Test
    void rollsBackInvitationQueueAndOperationWhenNotificationEnqueueFails() {
        User teacher = account(UserRole.TEACHER, uniqueEmail());
        transactions.executeWithoutResult(status -> doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Simulated enqueue failure");
        }).when(notifications).enqueue(any()));
        UUID operation = UUID.randomUUID();
        assertThatThrownBy(() -> create.execute(new CreateStudentInvitationCommand(operation, teacher.id(), uniqueEmail())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(dsl.fetchCount(TUTORING_STUDENT_INVITATIONS, TUTORING_STUDENT_INVITATIONS.TEACHER_USER_ID.eq(teacher.id()))).isZero();
        assertThat(dsl.fetchCount(TUTORING_COMMAND_OPERATIONS, TUTORING_COMMAND_OPERATIONS.OPERATION_ID.eq(operation))).isZero();
        assertThat(dsl.fetchCount(NOTIFICATION_INVITATION_EMAIL_DELIVERIES,
                NOTIFICATION_INVITATION_EMAIL_DELIVERIES.TEACHER_DISPLAY_NAME.eq(teacher.id().toString()))).isZero();
    }

    @Test
    void retriesOutboxEventAfterRollingBackReceiptAndInvitationBinding() {
        User teacher = account(UserRole.TEACHER, uniqueEmail());
        String email = uniqueEmail();
        InvitationCreatedResult invitation = create.execute(new CreateStudentInvitationCommand(UUID.randomUUID(), teacher.id(), email));
        User student = account(UserRole.STUDENT, email);
        AccountEmailVerifiedEvent event = new AccountEmailVerifiedEvent(UUID.randomUUID(), student.id(), email,
                AccountEmailVerificationPurpose.REGISTRATION, clock.instant());
        transactions.executeWithoutResult(status -> outbox.save(event));
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Simulated consumer failure");
        }).when(verifiedEmails).handle(any());
        dispatcher.processPending();
        assertThat(dsl.fetchCount(TUTORING_PROCESSED_IDENTITY_EVENTS, TUTORING_PROCESSED_IDENTITY_EVENTS.EVENT_ID.eq(event.eventId()))).isZero();
        assertThat(dsl.select(TUTORING_STUDENT_INVITATIONS.STUDENT_USER_ID).from(TUTORING_STUDENT_INVITATIONS)
                .where(TUTORING_STUDENT_INVITATIONS.ID.eq(invitation.invitationId())).fetchSingle().value1()).isNull();
        assertThat(dsl.select(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.PUBLISHED_AT).from(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX)
                .where(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.EVENT_ID.eq(event.eventId())).fetchSingle().value1()).isNull();

        doCallRealMethod().when(verifiedEmails).handle(any());
        dsl.update(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX).set(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.NEXT_ATTEMPT_AT, clock.instant().minusSeconds(1).atOffset(ZoneOffset.UTC))
                .where(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.EVENT_ID.eq(event.eventId())).execute();
        dispatcher.processPending();
        assertThat(dsl.fetchCount(TUTORING_PROCESSED_IDENTITY_EVENTS, TUTORING_PROCESSED_IDENTITY_EVENTS.EVENT_ID.eq(event.eventId()))).isEqualTo(1);
        assertThat(dsl.select(TUTORING_STUDENT_INVITATIONS.STUDENT_USER_ID).from(TUTORING_STUDENT_INVITATIONS)
                .where(TUTORING_STUDENT_INVITATIONS.ID.eq(invitation.invitationId())).fetchSingle().value1()).isEqualTo(student.id());
    }

    private User account(UserRole role, String email) {
        return transactions.execute(status -> {
            Instant now = clock.instant();
            User user = User.register(UUID.randomUUID(), new Email(email), new PasswordHash("fixture-password-hash"),
                    "Concurrency", "Test", LocalDate.of(2000, 1, 1), Set.of(role), now.minusSeconds(1));
            user.verifyRegistrationEmail(user.email(), now);
            users.save(user);
            accountIds.add(user.id());
            if (role == UserRole.TEACHER) {
                teachers.insert(TeacherProfile.create(user.id(), user.id().toString(), new ProfileEmail("contact@example.test"),
                        List.of(), Set.of(new SubjectCode("MATH")), null, null, null, null, null, now));
            } else {
                students.insert(StudentProfile.create(user.id(), "Student", new ProfileEmail("contact@example.test"),
                        List.of(), Set.of(), null, now));
            }
            return user;
        });
    }

    private <T> List<T> concurrently(Supplier<T> action) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<T> task = () -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent test did not start");
                }
                return action.get();
            };
            Future<T> first = executor.submit(task);
            Future<T> second = executor.submit(task);
            start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }

    private String uniqueEmail() {
        return "tutoring-concurrency-" + UUID.randomUUID() + "@example.test";
    }
}

