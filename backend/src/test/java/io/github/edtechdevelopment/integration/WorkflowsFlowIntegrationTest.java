package io.github.edtechdevelopment.integration;

import com.jayway.jsonpath.JsonPath;
import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.AccessTokenIssuer;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.infrastructure.messaging.event.AccountEmailEventDispatcher;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileEmail;
import io.github.edtechdevelopment.tutoring.domain.profile.model.StudentProfile;
import io.github.edtechdevelopment.workflows.application.onboarding.model.AddTeacherRoleCommand;
import io.github.edtechdevelopment.workflows.application.onboarding.port.in.AddTeacherRoleUseCase;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.Tables.*;
import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.Tables.*;
import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.*;
import static io.github.edtechdevelopment.workflows.infrastructure.persistence.data.generated.Tables.WORKFLOWS_OPERATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowsFlowIntegrationTest {

    private static final String PASSWORD = "StrongPassword42!";
    @Autowired MockMvc http;
    @Autowired DSLContext dsl;
    @Autowired Clock clock;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository students;
    @Autowired AccessTokenIssuer tokens;
    @Autowired JwtDecoder decoder;
    @Autowired AccountEmailEventDispatcher events;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired AddTeacherRoleUseCase addTeacher;
    private final Set<String> emails = new HashSet<>();
    private final Set<UUID> keys = new HashSet<>();
    private TransactionTemplate transactions;

    @BeforeEach
    void prepareTransactions() {
        transactions = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanOnlyOwnCommittedFixtures() {
        transactions.executeWithoutResult(status -> {
            Set<UUID> ids = dsl.select(IDENTITY_USER_EMAILS.USER_ID).from(IDENTITY_USER_EMAILS)
                    .where(IDENTITY_USER_EMAILS.EMAIL.in(emails)).fetchSet(IDENTITY_USER_EMAILS.USER_ID);
            var invitations = dsl.select(TUTORING_STUDENT_INVITATIONS.ID).from(TUTORING_STUDENT_INVITATIONS)
                    .where(TUTORING_STUDENT_INVITATIONS.TEACHER_USER_ID.in(ids));
            dsl.deleteFrom(NOTIFICATION_INVITATION_EMAIL_DELIVERIES)
                    .where(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.INVITATION_ID.in(invitations)).execute();
            dsl.deleteFrom(TUTORING_TEACHER_STUDENTS).where(TUTORING_TEACHER_STUDENTS.TEACHER_USER_ID.in(ids)
                    .or(TUTORING_TEACHER_STUDENTS.STUDENT_USER_ID.in(ids))).execute();
            dsl.deleteFrom(TUTORING_STUDENT_INVITATIONS).where(TUTORING_STUDENT_INVITATIONS.TEACHER_USER_ID.in(ids)).execute();
            dsl.deleteFrom(TUTORING_COMMAND_OPERATIONS).where(TUTORING_COMMAND_OPERATIONS.USER_ID.in(ids)).execute();
            dsl.deleteFrom(TUTORING_PROCESSED_IDENTITY_EVENTS).where(TUTORING_PROCESSED_IDENTITY_EVENTS.USER_ID.in(ids)).execute();
            dsl.deleteFrom(TUTORING_TEACHER_PROFILES).where(TUTORING_TEACHER_PROFILES.USER_ID.in(ids)).execute();
            dsl.deleteFrom(TUTORING_STUDENT_PROFILES).where(TUTORING_STUDENT_PROFILES.USER_ID.in(ids)).execute();
            dsl.deleteFrom(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX).where(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.USER_ID.in(ids)).execute();
            dsl.deleteFrom(NOTIFICATION_EMAIL_DELIVERIES).where(NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.in(emails)).execute();
            dsl.deleteFrom(WORKFLOWS_OPERATIONS).where(WORKFLOWS_OPERATIONS.OPERATION_ID.in(keys)).execute();
            dsl.deleteFrom(IDENTITY_USERS).where(IDENTITY_USERS.ID.in(ids)).execute();
        });
    }

    @Test
    void registersBothProfilesAndReturnsPendingVerificationWithoutTokens() throws Exception {
        String email = email();
        http.perform(post("/api/v1/auth/register").header("Idempotency-Key", key())
                        .contentType("application/json").content(registration(email, "BOTH")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.verificationExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        UUID id = userId(email);
        assertThat(dsl.fetchCount(TUTORING_TEACHER_PROFILES, TUTORING_TEACHER_PROFILES.USER_ID.eq(id))).isEqualTo(1);
        assertThat(dsl.fetchCount(TUTORING_STUDENT_PROFILES, TUTORING_STUDENT_PROFILES.USER_ID.eq(id))).isEqualTo(1);
        assertThat(dsl.select(IDENTITY_USERS.STATUS).from(IDENTITY_USERS).where(IDENTITY_USERS.ID.eq(id)).fetchSingle().value1())
                .isEqualTo("PENDING_EMAIL_VERIFICATION");
    }

    @Test
    void registersSingleRoleWithoutCreatingTheOtherProfile() throws Exception {
        for (String role : List.of("STUDENT", "TEACHER")) {
            String email = email();
            register(email, role, key());
            UUID id = userId(email);
            assertThat(dsl.fetchCount(TUTORING_STUDENT_PROFILES, TUTORING_STUDENT_PROFILES.USER_ID.eq(id)))
                    .isEqualTo(role.equals("STUDENT") ? 1 : 0);
            assertThat(dsl.fetchCount(TUTORING_TEACHER_PROFILES, TUTORING_TEACHER_PROFILES.USER_ID.eq(id)))
                    .isEqualTo(role.equals("TEACHER") ? 1 : 0);
        }
    }

    @Test
    void rejectsMissingAndExtraProfilesBeforeCreatingAnAccount() throws Exception {
        String missing = email();
        String missingBody = registration(missing, "STUDENT").replace(",\"studentProfile\":" + studentProfile(), "");
        http.perform(post("/api/v1/auth/register").header("Idempotency-Key", key())
                        .contentType("application/json").content(missingBody))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ROLE_PROFILE_MISMATCH"));
        assertNoAccount(missing);
        String extra = email();
        String extraBody = registration(extra, "STUDENT");
        extraBody = extraBody.substring(0, extraBody.length() - 1) + ",\"teacherProfile\":" + teacherProfile() + "}";
        http.perform(post("/api/v1/auth/register").header("Idempotency-Key", key())
                        .contentType("application/json").content(extraBody))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ROLE_PROFILE_MISMATCH"));
        assertNoAccount(extra);
    }

    @Test
    void rollsBackAccountVerificationDeliveryAndJournalWhenSubjectsAreUnknown() throws Exception {
        String email = email();
        UUID key = key();
        http.perform(post("/api/v1/auth/register").header("Idempotency-Key", key)
                        .contentType("application/json").content(registration(email, "BOTH").replace("MATH", "UNKNOWN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_SUBJECTS"));
        assertNoAccount(email);
        assertThat(dsl.fetchCount(IDENTITY_EMAIL_VERIFICATIONS, IDENTITY_EMAIL_VERIFICATIONS.TARGET_EMAIL.eq(email))).isZero();
        assertThat(dsl.fetchCount(NOTIFICATION_EMAIL_DELIVERIES, NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(email))).isZero();
        assertThat(dsl.fetchCount(WORKFLOWS_OPERATIONS, WORKFLOWS_OPERATIONS.OPERATION_ID.eq(key))).isZero();
    }

    @Test
    void returnsSameRegistrationAndDoesNotEnqueueAgainForAnEquivalentRepeat() throws Exception {
        String email = email();
        UUID key = key();
        String result = register(email, "BOTH", key);
        String reordered = registration(email.toUpperCase(), "BOTH")
                .replace("[\"STUDENT\",\"TEACHER\"]", "[\"TEACHER\",\"STUDENT\"]")
                .replace("\"firstName\":\"Test\"", "\"firstName\":\"  Test  \"");
        http.perform(post("/api/v1/auth/register").header("Idempotency-Key", key)
                        .contentType("application/json").content(reordered))
                .andExpect(status().isAccepted()).andExpect(content().json(result));
        assertThat(dsl.fetchCount(IDENTITY_USER_EMAILS, IDENTITY_USER_EMAILS.EMAIL.eq(email))).isEqualTo(1);
        assertThat(dsl.fetchCount(NOTIFICATION_EMAIL_DELIVERIES, NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(email))).isEqualTo(1);
        String stored = dsl.select(WORKFLOWS_OPERATIONS.RESULT_PAYLOAD).from(WORKFLOWS_OPERATIONS)
                .where(WORKFLOWS_OPERATIONS.OPERATION_ID.eq(key)).fetchSingle().value1().data();
        assertThat(stored).doesNotContain(PASSWORD, "token", "birthDate", "passwordHash");
    }

    @Test
    void conflictsForChangedPasswordOrEmailWithTheSameOperationKey() throws Exception {
        String email = email();
        UUID key = key();
        register(email, "STUDENT", key);
        for (String changed : List.of(registration(email, "STUDENT").replace(PASSWORD, "OtherPassword42!"),
                registration(email(), "STUDENT"))) {
            http.perform(post("/api/v1/auth/register").header("Idempotency-Key", key)
                            .contentType("application/json").content(changed))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        }
    }

    @Test
    void confirmsAccountAndReturnsOnlyItsOwnMeData() throws Exception {
        String email = email();
        register(email, "BOTH", key());
        String token = confirm(email).getResponse().getContentAsString();
        String access = JsonPath.read(token, "$.accessToken");
        http.perform(get("/api/v1/me").header("Authorization", "Bearer " + access).param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId(email).toString()))
                .andExpect(jsonPath("$.user.birthDate").value("2000-01-01"))
                .andExpect(jsonPath("$.teacherProfile.birthDate").doesNotExist())
                .andExpect(jsonPath("$.studentProfile.birthDate").doesNotExist())
                .andExpect(jsonPath("$.teacherProfile.age").isNumber())
                .andExpect(jsonPath("$.studentProfile.contactEmail").value("contact@example.test"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
        http.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void addsTeacherRoleAtomicallyAndRefreshesJwtRoles() throws Exception {
        String email = email();
        register(email, "STUDENT", key());
        MvcResult confirmed = confirm(email);
        String access = JsonPath.read(confirmed.getResponse().getContentAsString(), "$.accessToken");
        assertThat(decoder.decode(access).getClaimAsStringList("roles")).containsExactly("STUDENT");
        UUID key = key();
        String added = http.perform(post("/api/v1/me/roles/teacher").header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", key).contentType("application/json").content(teacherProfile()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherProfile.subjectCodes[0]").value("MATH"))
                .andExpect(jsonPath("$.studentProfile.displayName").value("Student"))
                .andReturn().getResponse().getContentAsString();
        http.perform(post("/api/v1/me/roles/teacher").header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", key).contentType("application/json").content(teacherProfile()))
                .andExpect(status().isCreated()).andExpect(content().json(added));
        String refreshCookie = confirmed.getResponse().getHeader(HttpHeaders.SET_COOKIE).split(";", 2)[0];
        String refreshed = http.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("REFRESH_TOKEN", refreshCookie.substring(refreshCookie.indexOf('=') + 1)))
                        .header(HttpHeaders.ORIGIN, "http://frontend.example:3000"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String freshToken = JsonPath.read(refreshed, "$.accessToken");
        assertThat(decoder.decode(freshToken).getClaimAsStringList("roles")).containsExactlyInAnyOrder("STUDENT", "TEACHER");
        assertThat(dsl.fetchCount(TUTORING_TEACHER_PROFILES, TUTORING_TEACHER_PROFILES.USER_ID.eq(userId(email)))).isEqualTo(1);
    }

    @Test
    void addsStudentRoleWithAnEmptySubjectSet() throws Exception {
        String email = email();
        register(email, "TEACHER", key());
        String access = JsonPath.read(confirm(email).getResponse().getContentAsString(), "$.accessToken");
        http.perform(post("/api/v1/me/roles/student").header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", key()).contentType("application/json").content(studentProfile()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.studentProfile.subjectCodes").isEmpty())
                .andExpect(jsonPath("$.teacherProfile.displayName").value("Teacher"));
    }

    @Test
    void rollsBackTheNewRoleWhenItsProfileCannotBeCreated() throws Exception {
        String email = email();
        register(email, "STUDENT", key());
        String access = JsonPath.read(confirm(email).getResponse().getContentAsString(), "$.accessToken");
        UUID key = key();
        http.perform(post("/api/v1/me/roles/teacher").header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", key).contentType("application/json").content(teacherProfile().replace("MATH", "UNKNOWN")))
                .andExpect(status().isBadRequest());
        UUID id = userId(email);
        assertThat(dsl.select(IDENTITY_USER_ROLES.ROLE).from(IDENTITY_USER_ROLES)
                .where(IDENTITY_USER_ROLES.USER_ID.eq(id)).fetchSet(IDENTITY_USER_ROLES.ROLE)).containsExactly("STUDENT");
        assertThat(dsl.fetchCount(TUTORING_TEACHER_PROFILES, TUTORING_TEACHER_PROFILES.USER_ID.eq(id))).isZero();
        assertThat(dsl.fetchCount(WORKFLOWS_OPERATIONS, WORKFLOWS_OPERATIONS.OPERATION_ID.eq(key))).isZero();
    }

    @Test
    void rejectsAnExistingRoleAndChangedOnboardingPayload() throws Exception {
        String email = email();
        register(email, "STUDENT", key());
        String access = JsonPath.read(confirm(email).getResponse().getContentAsString(), "$.accessToken");
        http.perform(post("/api/v1/me/roles/student").header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", key()).contentType("application/json").content(studentProfile()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROLE_ALREADY_ENABLED"));
        UUID key = key();
        http.perform(post("/api/v1/me/roles/teacher").header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", key).contentType("application/json").content(teacherProfile()))
                .andExpect(status().isCreated());
        http.perform(post("/api/v1/me/roles/teacher").header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", key).contentType("application/json").content(teacherProfile().replace("Teacher", "Changed")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void rejectsSuspendedAccountEvenWithAnUnexpiredJwt() throws Exception {
        String email = email();
        register(email, "STUDENT", key());
        String access = JsonPath.read(confirm(email).getResponse().getContentAsString(), "$.accessToken");
        dsl.update(IDENTITY_USERS).set(IDENTITY_USERS.STATUS, "SUSPENDED").where(IDENTITY_USERS.ID.eq(userId(email))).execute();
        http.perform(post("/api/v1/me/roles/teacher").header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", key()).contentType("application/json").content(teacherProfile()))
                .andExpect(status().isForbidden());
        assertThat(dsl.fetchCount(TUTORING_TEACHER_PROFILES, TUTORING_TEACHER_PROFILES.USER_ID.eq(userId(email)))).isZero();
    }

    @Test
    void reportsMissingProfileAsAnInvariantError() throws Exception {
        String email = email();
        Instant now = clock.instant();
        User user = User.register(UUID.randomUUID(), new Email(email), new PasswordHash("fixture"), "Test", "User",
                LocalDate.of(2000, 1, 1), Set.of(UserRole.STUDENT), now.minusSeconds(1));
        user.verifyRegistrationEmail(user.email(), now);
        transactions.executeWithoutResult(status -> users.save(user));
        http.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokens.issue(user, now).value()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("PROFILE_INCONSISTENCY"));
    }

    @Test
    void requiresAValidKeyAndRejectsOversizedOrForgedRequests() throws Exception {
        String email = email();
        http.perform(post("/api/v1/auth/register").contentType("application/json").content(registration(email, "STUDENT")))
                .andExpect(status().isBadRequest());
        http.perform(post("/api/v1/auth/register").header("Idempotency-Key", "invalid")
                        .contentType("application/json").content(registration(email, "STUDENT")))
                .andExpect(status().isBadRequest());
        http.perform(post("/api/v1/auth/register").header("Idempotency-Key", key())
                        .contentType("application/json").content("x".repeat(1048577)))
                .andExpect(status().isPayloadTooLarge());
        register(email, "STUDENT", key());
        String access = JsonPath.read(confirm(email).getResponse().getContentAsString(), "$.accessToken");
        String forged = teacherProfile();
        forged = forged.substring(0, forged.length() - 1) + ",\"userId\":\"" + UUID.randomUUID() + "\"}";
        http.perform(post("/api/v1/me/roles/teacher").header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", key()).contentType("application/json").content(forged))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completesTheInvitationRegistrationVerificationAndAcceptancePath() throws Exception {
        String teacherEmail = email();
        register(teacherEmail, "TEACHER", key());
        String teacherToken = JsonPath.read(confirm(teacherEmail).getResponse().getContentAsString(), "$.accessToken");
        String studentEmail = email();
        String created = http.perform(post("/api/v1/tutoring/invitations").header("Authorization", "Bearer " + teacherToken)
                        .header("Idempotency-Key", key()).contentType("application/json").content("{\"studentEmail\":\"" + studentEmail + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String invitationId = JsonPath.read(created, "$.invitationId");
        register(studentEmail, "STUDENT", key());
        String studentToken = JsonPath.read(confirm(studentEmail).getResponse().getContentAsString(), "$.accessToken");
        events.processPending();
        http.perform(get("/api/v1/tutoring/invitations/incoming").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].invitationId").value(invitationId))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
        http.perform(post("/api/v1/tutoring/invitations/" + invitationId + "/accept")
                        .header("Authorization", "Bearer " + studentToken).header("Idempotency-Key", key()))
                .andExpect(status().isOk());
        http.perform(get("/api/v1/tutoring/relationships/students").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].student.userId").value(userId(studentEmail).toString()));
    }

    @Test
    void anotherConfirmedEmailDoesNotReceiveTheOriginalInvitation() throws Exception {
        String teacherEmail = email();
        register(teacherEmail, "TEACHER", key());
        String teacherToken = JsonPath.read(confirm(teacherEmail).getResponse().getContentAsString(), "$.accessToken");
        String recipientEmail = email();
        String created = http.perform(post("/api/v1/tutoring/invitations").header("Authorization", "Bearer " + teacherToken)
                        .header("Idempotency-Key", key()).contentType("application/json").content("{\"studentEmail\":\"" + recipientEmail + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String otherEmail = email();
        register(otherEmail, "STUDENT", key());
        String token = JsonPath.read(confirm(otherEmail).getResponse().getContentAsString(), "$.accessToken");
        events.processPending();
        http.perform(get("/api/v1/tutoring/invitations/incoming").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        http.perform(post("/api/v1/tutoring/invitations/" + JsonPath.read(created, "$.invitationId") + "/accept")
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", key()))
                .andExpect(status().isNotFound());
    }

    private String register(String email, String role, UUID key) throws Exception {
        return http.perform(post("/api/v1/auth/register").header("Idempotency-Key", key)
                        .contentType("application/json").content(registration(email, role)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
    }

    private MvcResult confirm(String email) throws Exception {
        String url = dsl.select(NOTIFICATION_EMAIL_DELIVERIES.CONFIRMATION_URL).from(NOTIFICATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(email)).fetchSingle().value1();
        String token = UriComponentsBuilder.fromUri(URI.create(url)).build().getQueryParams().getFirst("token");
        return http.perform(post("/api/v1/auth/email-verification/confirm").contentType("application/json")
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk()).andReturn();
    }

    private UUID userId(String email) {
        return dsl.select(IDENTITY_USER_EMAILS.USER_ID).from(IDENTITY_USER_EMAILS)
                .where(IDENTITY_USER_EMAILS.EMAIL.eq(email)).fetchSingle().value1();
    }

    private void assertNoAccount(String email) {
        assertThat(dsl.fetchCount(IDENTITY_USER_EMAILS, IDENTITY_USER_EMAILS.EMAIL.eq(email))).isZero();
    }

    private String email() {
        String email = "workflows-stage67-" + UUID.randomUUID() + "@example.test";
        emails.add(email);
        return email;
    }

    private UUID key() {
        UUID key = UUID.randomUUID();
        keys.add(key);
        return key;
    }

    private String registration(String email, String role) {
        String roles = role.equals("BOTH") ? "[\"STUDENT\",\"TEACHER\"]" : "[\"" + role + "\"]";
        String profile = role.equals("STUDENT") ? ",\"studentProfile\":" + studentProfile()
                : role.equals("TEACHER") ? ",\"teacherProfile\":" + teacherProfile()
                : ",\"studentProfile\":" + studentProfile() + ",\"teacherProfile\":" + teacherProfile();
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"firstName\":\"Test\",\"lastName\":\"User\","
                + "\"birthDate\":\"2000-01-01\",\"roles\":" + roles + profile + "}";
    }

    private String studentProfile() {
        return "{\"displayName\":\"Student\",\"contactEmail\":\"contact@example.test\",\"contactDetails\":[],\"subjectCodes\":[]}";
    }

    private String teacherProfile() {
        return "{\"displayName\":\"Teacher\",\"contactEmail\":\"contact@example.test\",\"contactDetails\":[],\"subjectCodes\":[\"MATH\"]}";
    }
}

