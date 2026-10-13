package io.github.edtechdevelopment.integration;

import com.jayway.jsonpath.JsonPath;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.AccessTokenIssuer;
import io.github.edtechdevelopment.identity.api.event.AccountEmailVerifiedEvent;
import io.github.edtechdevelopment.identity.api.model.AccountEmailVerificationPurpose;
import io.github.edtechdevelopment.identity.domain.user.model.*;
import io.github.edtechdevelopment.identity.infrastructure.messaging.event.AccountEmailEventOutbox;
import io.github.edtechdevelopment.identity.infrastructure.messaging.event.AccountEmailEventDispatcher;
import io.github.edtechdevelopment.notifications.application.port.out.persistence.InvitationEmailDeliveryRepository;
import io.github.edtechdevelopment.notifications.application.model.ClaimedInvitationEmail;
import io.github.edtechdevelopment.notifications.domain.delivery.model.DeliveryStatus;
import io.github.edtechdevelopment.tutoring.application.integration.identity.model.command.HandleAccountEmailVerifiedCommand;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.in.HandleAccountEmailVerifiedUseCase;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.StudentInvitationRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.*;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.*;
import io.github.edtechdevelopment.tutoring.domain.profile.model.*;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.*;
import java.util.*;

import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.*;
import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.Tables.NOTIFICATION_INVITATION_EMAIL_DELIVERIES;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.Tables.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TutoringInfrastructureIntegrationTest {

    @Autowired MockMvc http;
    @Autowired DSLContext dsl;
    @Autowired Clock clock;
    @Autowired UserRepository users;
    @Autowired AccessTokenIssuer tokens;
    @Autowired TeacherProfileRepository teachers;
    @Autowired StudentProfileRepository students;
    @Autowired StudentInvitationRepository invitations;
    @Autowired HandleAccountEmailVerifiedUseCase verifiedEmails;
    @Autowired AccountEmailEventOutbox outbox;
    @Autowired AccountEmailEventDispatcher eventDispatcher;
    @Autowired InvitationEmailDeliveryRepository deliveries;

    @Test
    void roundTripsProfilesAndSynchronizesSubjectsWithoutChangingCreationTime() {
        User teacher = account(UserRole.TEACHER);
        TeacherProfile profile = teacherProfile(teacher);
        Instant createdAt = profile.createdAt();
        assertThat(teachers.findByUserIds(Set.of(teacher.id())).get(teacher.id()).subjectCodes())
                .containsExactly(new SubjectCode("CHEMISTRY"));
        TeacherProfile saved = teachers.findByUserIdForUpdate(teacher.id()).orElseThrow();
        saved.changeSubjects(Set.of(new SubjectCode("MATH"), new SubjectCode("PHYSICS")));
        saved.updateDetails("Updated", List.of("telegram"), null, null, 0, null, null);
        saved.changeContactEmail(new ProfileEmail("Another@Example.test"));
        teachers.update(saved);
        TeacherProfile updated = teachers.findByUserId(teacher.id()).orElseThrow();
        assertThat(updated.createdAt()).isEqualTo(createdAt);
        assertThat(updated.contactEmail().value()).isEqualTo("another@example.test");
        assertThat(updated.subjectCodes()).containsExactlyInAnyOrder(new SubjectCode("MATH"), new SubjectCode("PHYSICS"));
        assertThat(updated.photoUrl()).isNull();

        User student = account(UserRole.STUDENT);
        students.insert(StudentProfile.create(student.id(), "Student", new ProfileEmail("contact@example.test"),
                List.of(), Set.of(new SubjectCode("MATH")), null, clock.instant()));
        StudentProfile savedStudent = students.findByUserIdForUpdate(student.id()).orElseThrow();
        savedStudent.changeSubjects(Set.of());
        students.update(savedStudent);
        assertThat(students.findByUserIds(Set.of(student.id())).get(student.id()).subjectCodes()).isEmpty();
        assertThat(teachers.findByUserIds(Set.of())).isEmpty();
    }

    @Test
    void returnsShortPublicCardsAndFullProfileWithAgeButWithoutBirthDate() throws Exception {
        User teacher = account(UserRole.TEACHER);
        teacherProfile(teacher);
        http.perform(get("/api/v1/tutoring/subjects"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10));
        http.perform(get("/api/v1/tutoring/profiles/teachers").param("subjectCode", "CHEMISTRY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.userId == '" + teacher.id() + "')].age").isNotEmpty())
                .andExpect(jsonPath("$.items[0].contactEmail").doesNotExist())
                .andExpect(jsonPath("$.items[0].education").doesNotExist())
                .andExpect(jsonPath("$.items[0].birthDate").doesNotExist());
        http.perform(get("/api/v1/tutoring/profiles/teachers/" + teacher.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("contact@example.test"))
                .andExpect(jsonPath("$.age").value(Period.between(teacher.birthDate(), LocalDate.now(clock)).getYears()))
                .andExpect(jsonPath("$.birthDate").doesNotExist());
        dsl.update(IDENTITY_USERS).set(IDENTITY_USERS.STATUS, "SUSPENDED").where(IDENTITY_USERS.ID.eq(teacher.id())).execute();
        http.perform(get("/api/v1/tutoring/profiles/teachers/" + teacher.id())).andExpect(status().isNotFound());
    }

    @Test
    void paginatesTwentyCardsWithoutDuplicatesWhenCreationTimesAreEqual() throws Exception {
        Set<String> expected = new HashSet<>();
        Instant createdAt = clock.instant().minusSeconds(1);
        for (int i = 0; i < 22; i++) {
            User teacher = account(UserRole.TEACHER, uniqueEmail(), LocalDate.of(1900, 1, 1));
            expected.add(teacher.id().toString());
            teachers.insert(TeacherProfile.create(teacher.id(), "Teacher " + i, new ProfileEmail("contact@example.test"),
                    List.of(), Set.of(new SubjectCode("GEOGRAPHY")), null, null, null, null, null, createdAt));
        }
        String first = http.perform(get("/api/v1/tutoring/profiles/teachers")
                        .param("subjectCode", "GEOGRAPHY").param("minAge", "120"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(20))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.nextCursor");
        String second = http.perform(get("/api/v1/tutoring/profiles/teachers")
                        .param("subjectCode", "GEOGRAPHY").param("minAge", "120").param("cursor", cursor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").isEmpty()).andReturn().getResponse().getContentAsString();
        List<String> ids = new ArrayList<>(JsonPath.<List<String>>read(first, "$.items[*].userId"));
        ids.addAll(JsonPath.<List<String>>read(second, "$.items[*].userId"));
        assertThat(ids).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
        http.perform(get("/api/v1/tutoring/profiles/teachers").param("subjectCode", "MATH").param("cursor", cursor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        http.perform(get("/api/v1/tutoring/profiles/students").param("cursor", cursor))
                .andExpect(status().isBadRequest());
        http.perform(get("/api/v1/tutoring/profiles/teachers").param("cursor", "broken"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesOnlyTheAuthenticatedProfileAndValidatesPhotoAndSubjects() throws Exception {
        User teacher = account(UserRole.TEACHER);
        TeacherProfile original = teacherProfile(teacher);
        http.perform(put("/api/v1/tutoring/profiles/teacher").header("Authorization", bearer(teacher))
                        .contentType("application/json").content(teacherUpdate("https://example.test/photo.png", "MATH")))
                .andExpect(status().isNoContent());
        TeacherProfile saved = teachers.findByUserId(teacher.id()).orElseThrow();
        assertThat(saved.createdAt()).isEqualTo(original.createdAt());
        assertThat(saved.contactEmail().value()).isEqualTo("public@example.test");
        assertThat(saved.photoUrl()).isEqualTo(URI.create("https://example.test/photo.png"));
        for (String invalid : List.of("file:///tmp/photo.png", "/photo.png", "https:/photo.png", "")) {
            http.perform(put("/api/v1/tutoring/profiles/teacher").header("Authorization", bearer(teacher))
                            .contentType("application/json").content(teacherUpdate(invalid, "MATH")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("photoUrl"));
        }
        http.perform(put("/api/v1/tutoring/profiles/teacher").header("Authorization", bearer(teacher))
                        .contentType("application/json").content(teacherUpdate(null, "UNKNOWN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_SUBJECTS"));
        http.perform(put("/api/v1/tutoring/profiles/teacher").header("Authorization", bearer(teacher))
                        .contentType("application/json").content(teacherUpdate(null, "MATH")))
                .andExpect(status().isNoContent());
        assertThat(teachers.findByUserId(teacher.id()).orElseThrow().photoUrl()).isNull();
    }

    @Test
    void rejectsAnonymousWrongRoleSuspendedAccountsAndForgedActorFields() throws Exception {
        User student = account(UserRole.STUDENT);
        String token = bearer(student);
        http.perform(put("/api/v1/tutoring/profiles/teacher").contentType("application/json")
                .content(teacherUpdate(null, "MATH"))).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/tutoring/invitations").header("Authorization", token)
                .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                .content("{\"studentEmail\":\"student@example.test\"}")).andExpect(status().isForbidden());
        students.insert(StudentProfile.create(student.id(), "Student", new ProfileEmail("public@example.test"),
                List.of(), Set.of(), null, clock.instant()));
        String body = "{\"displayName\":\"Student\",\"contactEmail\":\"public@example.test\",\"contactDetails\":[],\"subjectCodes\":[]}";
        http.perform(put("/api/v1/tutoring/profiles/student").header("Authorization", token)
                .contentType("application/json").content(body)).andExpect(status().isNoContent());
        http.perform(put("/api/v1/tutoring/profiles/student").header("Authorization", token)
                .contentType("application/json").content(body.replace("}", ",\"userId\":\"" + UUID.randomUUID() + "\"}")))
                .andExpect(status().isBadRequest());
        dsl.update(IDENTITY_USERS).set(IDENTITY_USERS.STATUS, "SUSPENDED").where(IDENTITY_USERS.ID.eq(student.id())).execute();
        http.perform(get("/api/v1/tutoring/invitations/incoming").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportsBrowserCorsAndLimitsRequestBodies() throws Exception {
        http.perform(options("/api/v1/tutoring/profiles/teacher").header("Origin", "http://frontend.example:3000")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://frontend.example:3000"));
        User teacher = account(UserRole.TEACHER);
        http.perform(post("/api/v1/tutoring/invitations").header("Authorization", bearer(teacher))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json").content("x".repeat(1048577)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void createsUnknownRecipientInvitationAndReplaysExactlyOnePersistedResult() throws Exception {
        User teacher = account(UserRole.TEACHER);
        teacherProfile(teacher);
        String email = uniqueEmail();
        UUID operation = UUID.randomUUID();
        String result = createInvitation(teacher, email, operation);
        String replay = createInvitation(teacher, email.toUpperCase(), operation);
        assertThat(replay).isEqualTo(result);
        UUID id = UUID.fromString(JsonPath.read(result, "$.invitationId"));
        StudentInvitation saved = invitations.findByIdForUpdate(id).orElseThrow();
        assertThat(saved.studentUserId()).isNull();
        assertThat(dsl.fetchCount(NOTIFICATION_INVITATION_EMAIL_DELIVERIES,
                NOTIFICATION_INVITATION_EMAIL_DELIVERIES.INVITATION_ID.eq(id))).isEqualTo(1);
        assertThat(dsl.select(TUTORING_COMMAND_OPERATIONS.RESULT_PAYLOAD).from(TUTORING_COMMAND_OPERATIONS)
                .where(TUTORING_COMMAND_OPERATIONS.OPERATION_ID.eq(operation)).fetchSingle().value1().data()).contains(id.toString());
        http.perform(post("/api/v1/tutoring/invitations").header("Authorization", bearer(teacher))
                        .header("Idempotency-Key", operation).contentType("application/json")
                        .content("{\"studentEmail\":\"" + uniqueEmail() + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        http.perform(post("/api/v1/tutoring/invitations").header("Authorization", bearer(teacher))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                        .content("{\"studentEmail\":\"" + email + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_PENDING"));
    }

    @Test
    void attachesVerifiedEmailWithoutAutomaticallyAcceptingAndAllowsRejectBeforeStudentOnboarding() throws Exception {
        User teacher = account(UserRole.TEACHER);
        teacherProfile(teacher);
        String email = uniqueEmail();
        UUID id = UUID.fromString(JsonPath.read(createInvitation(teacher, email, UUID.randomUUID()), "$.invitationId"));
        User recipient = account(UserRole.TEACHER, email, LocalDate.of(2000, 1, 1));
        HandleAccountEmailVerifiedCommand event = new HandleAccountEmailVerifiedCommand(UUID.randomUUID(), recipient.id(), email, clock.instant());
        verifiedEmails.handle(event);
        verifiedEmails.handle(event);
        assertThat(invitations.findByIdForUpdate(id).orElseThrow().studentUserId()).isEqualTo(recipient.id());
        assertThat(invitations.findByIdForUpdate(id).orElseThrow().status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(dsl.fetchCount(TUTORING_PROCESSED_IDENTITY_EVENTS, TUTORING_PROCESSED_IDENTITY_EVENTS.EVENT_ID.eq(event.eventId()))).isEqualTo(1);
        http.perform(get("/api/v1/tutoring/invitations/incoming").header("Authorization", bearer(recipient)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].invitationId").value(id.toString()));
        http.perform(post("/api/v1/tutoring/invitations/" + id + "/accept").header("Authorization", bearer(recipient))
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDENT_PROFILE_REQUIRED"));
        http.perform(post("/api/v1/tutoring/invitations/" + id + "/reject").header("Authorization", bearer(recipient))
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void acceptsOnceAndReturnsRelationshipProfilesFromBothSides() throws Exception {
        User teacher = account(UserRole.TEACHER);
        teacherProfile(teacher);
        User student = account(UserRole.STUDENT);
        students.insert(StudentProfile.create(student.id(), "Student", new ProfileEmail("contact@example.test"),
                List.of(), Set.of(), null, clock.instant()));
        UUID id = UUID.fromString(JsonPath.read(createInvitation(teacher, student.email().value(), UUID.randomUUID()), "$.invitationId"));
        UUID operation = UUID.randomUUID();
        String result = http.perform(post("/api/v1/tutoring/invitations/" + id + "/accept")
                        .header("Authorization", bearer(student)).header("Idempotency-Key", operation))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        http.perform(post("/api/v1/tutoring/invitations/" + id + "/accept")
                        .header("Authorization", bearer(student)).header("Idempotency-Key", operation))
                .andExpect(status().isOk()).andExpect(content().json(result));
        assertThat(dsl.fetchCount(TUTORING_TEACHER_STUDENTS, TUTORING_TEACHER_STUDENTS.TEACHER_USER_ID.eq(teacher.id()))).isEqualTo(1);
        http.perform(get("/api/v1/tutoring/relationships/students").header("Authorization", bearer(teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].student.userId").value(student.id().toString()));
        http.perform(get("/api/v1/tutoring/relationships/teachers").header("Authorization", bearer(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].teacher.userId").value(teacher.id().toString()));
        User stranger = account(UserRole.STUDENT);
        http.perform(post("/api/v1/tutoring/invitations/" + id + "/reject")
                        .header("Authorization", bearer(stranger)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void keepsExistingInvitationOwnershipWhenEmailMovesToAnotherAccount() throws Exception {
        User teacher = account(UserRole.TEACHER);
        teacherProfile(teacher);
        User original = account(UserRole.STUDENT);
        UUID id = UUID.fromString(JsonPath.read(createInvitation(teacher, original.email().value(), UUID.randomUUID()), "$.invitationId"));
        String previousEmail = original.email().value();
        dsl.update(IDENTITY_USER_EMAILS).set(IDENTITY_USER_EMAILS.EMAIL, uniqueEmail())
                .where(IDENTITY_USER_EMAILS.USER_ID.eq(original.id())).execute();
        User newOwner = account(UserRole.STUDENT, previousEmail, LocalDate.of(2000, 1, 1));
        verifiedEmails.handle(new HandleAccountEmailVerifiedCommand(UUID.randomUUID(), newOwner.id(), previousEmail, clock.instant()));
        assertThat(invitations.findByIdForUpdate(id).orElseThrow().studentUserId()).isEqualTo(original.id());
        http.perform(get("/api/v1/tutoring/invitations/incoming").header("Authorization", bearer(newOwner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void deliversAccountEmailEventFromDurableOutboxAndRecordsReceipt() throws Exception {
        User teacher = account(UserRole.TEACHER);
        teacherProfile(teacher);
        String email = uniqueEmail();
        UUID id = UUID.fromString(JsonPath.read(createInvitation(teacher, email, UUID.randomUUID()), "$.invitationId"));
        User student = account(UserRole.STUDENT, email, LocalDate.of(2000, 1, 1));
        AccountEmailVerifiedEvent event = new AccountEmailVerifiedEvent(UUID.randomUUID(), student.id(), email,
                AccountEmailVerificationPurpose.REGISTRATION, clock.instant());
        outbox.save(event);
        eventDispatcher.processPending();
        assertThat(dsl.select(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.PUBLISHED_AT).from(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX)
                .where(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.EVENT_ID.eq(event.eventId())).fetchSingle().value1()).isNotNull();
        assertThat(invitations.findByIdForUpdate(id).orElseThrow().studentUserId()).isEqualTo(student.id());
    }

    @Test
    void recoversEmailClaimsAndRejectsAcknowledgementFromPreviousWorker() throws Exception {
        User teacher = account(UserRole.TEACHER);
        teacherProfile(teacher);
        UUID id = UUID.fromString(JsonPath.read(createInvitation(teacher, uniqueEmail(), UUID.randomUUID()), "$.invitationId"));
        Instant now = clock.instant().plusSeconds(1);
        ClaimedInvitationEmail first = deliveries.claimPending(now, now.minusSeconds(60), 100).stream()
                .filter(item -> item.delivery().invitationId().equals(id)).findFirst().orElseThrow();
        Instant later = now.plusSeconds(61);
        ClaimedInvitationEmail second = deliveries.claimPending(later, later.minusSeconds(60), 100).stream()
                .filter(item -> item.delivery().invitationId().equals(id)).findFirst().orElseThrow();
        assertThat(first.claimId()).isNotEqualTo(second.claimId());
        assertThat(deliveries.complete(first.delivery().id(), first.claimId(), DeliveryStatus.SENT, later)).isFalse();
        assertThat(deliveries.complete(second.delivery().id(), second.claimId(), DeliveryStatus.SENT, later)).isTrue();
        assertThat(dsl.select(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.INVITATION_URL).from(NOTIFICATION_INVITATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.INVITATION_ID.eq(id)).fetchSingle().value1()).isNull();
    }

    @Test
    void filtersInvitationStatusBeforeApplyingPageLimitAndKeepsSnapshot() {
        User teacher = account(UserRole.TEACHER);
        teacherProfile(teacher);
        User student = account(UserRole.STUDENT);
        Instant now = clock.instant();
        StudentInvitation old = StudentInvitation.create(UUID.randomUUID(), teacher.id(), new InvitationEmail(uniqueEmail()),
                student.id(), now.minus(Duration.ofDays(31)));
        old.expire(now);
        invitations.insert(old);
        StudentInvitation recent = StudentInvitation.create(UUID.randomUUID(), teacher.id(), new InvitationEmail(uniqueEmail()),
                student.id(), now.minusSeconds(1));
        invitations.insert(recent);
        assertThat(invitations.findIncomingPage(student.id(), InvitationStatus.EXPIRED, now, null, 1))
                .singleElement().satisfies(row -> assertThat(row.invitation().id()).isEqualTo(old.id()));
        StudentInvitation late = StudentInvitation.create(UUID.randomUUID(), teacher.id(), new InvitationEmail(uniqueEmail()),
                null, now.minusSeconds(1));
        late.attachStudent(student.id(), now.plusSeconds(1));
        invitations.insert(late);
        assertThat(invitations.findIncomingPage(student.id(), null, now, null, 10))
                .hasSize(2);
    }

    private String createInvitation(User teacher, String email, UUID operation) throws Exception {
        return http.perform(post("/api/v1/tutoring/invitations").header("Authorization", bearer(teacher))
                        .header("Idempotency-Key", operation).contentType("application/json")
                        .content("{\"studentEmail\":\"" + email + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private User account(UserRole role) {
        return account(role, uniqueEmail(), LocalDate.of(2000, 1, 1));
    }

    private User account(UserRole role, String email, LocalDate birthDate) {
        Instant createdAt = clock.instant().minusSeconds(60);
        User user = User.register(UUID.randomUUID(), new Email(email), new PasswordHash("fixture-password-hash"),
                "Test", "Account", birthDate, Set.of(role), createdAt);
        user.verifyRegistrationEmail(user.email(), createdAt.plusSeconds(1));
        users.save(user);
        return user;
    }

    private TeacherProfile teacherProfile(User user) {
        TeacherProfile profile = TeacherProfile.create(user.id(), "Teacher", new ProfileEmail("contact@example.test"),
                List.of("telegram"), Set.of(new SubjectCode("CHEMISTRY")), "Teaching chemistry", "University", 0,
                "Moscow", URI.create("https://example.test/photo.png"), clock.instant());
        teachers.insert(profile);
        return profile;
    }

    private String uniqueEmail() {
        return "tutoring-stage5-" + UUID.randomUUID() + "@example.test";
    }

    private String bearer(User user) {
        return "Bearer " + tokens.issue(user, clock.instant()).value();
    }

    private String teacherUpdate(String photo, String subject) {
        String photoJson = photo == null ? "null" : "\"" + photo + "\"";
        return "{\"displayName\":\"Updated\",\"contactEmail\":\"Public@Example.test\",\"contactDetails\":[],\"subjectCodes\":[\""
                + subject + "\"],\"photoUrl\":" + photoJson + "}";
    }
}

