package io.github.edtechdevelopment.workflows.infrastructure.idempotency;

import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.StudentProfileData;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;
import io.github.edtechdevelopment.workflows.application.onboarding.model.AddTeacherRoleCommand;
import io.github.edtechdevelopment.workflows.application.registration.model.RegistrationCommand;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class HmacWorkflowFingerprintTest {

    @Test
    void canonicalizesEmailsNamesAndSubjectSetsWithoutNormalizingPasswords() {
        HmacWorkflowFingerprint fingerprint = service(new byte[32]);
        RegistrationCommand first = registration("test@example.test", "StrongPassword42!", "Test", Set.of("MATH", "PHYSICS"));
        RegistrationCommand equivalent = registration(" TEST@EXAMPLE.TEST ", "StrongPassword42!", "  Test  ", Set.of("PHYSICS", "MATH"));
        assertThat(fingerprint.registration(first)).isEqualTo(fingerprint.registration(equivalent)).matches("[0-9a-f]{64}");
        assertThat(fingerprint.registration(registration("test@example.test", "OtherPassword42!", "Test", Set.of("MATH", "PHYSICS"))))
                .isNotEqualTo(fingerprint.registration(first));
        assertThat(first.toString()).doesNotContain(first.rawPassword(), first.email());
    }

    @Test
    void preservesContactOrderAndOptionalFieldDifferences() {
        HmacWorkflowFingerprint fingerprint = service(new byte[32]);
        UUID actor = UUID.randomUUID();
        TeacherProfileData first = teacher(List.of("a", "b"), null);
        TeacherProfileData reordered = teacher(List.of("b", "a"), null);
        TeacherProfileData emptyDescription = teacher(List.of("a", "b"), "");
        String value = fingerprint.teacherOnboarding(new AddTeacherRoleCommand(UUID.randomUUID(), actor, first));
        assertThat(fingerprint.teacherOnboarding(new AddTeacherRoleCommand(UUID.randomUUID(), actor, reordered))).isNotEqualTo(value);
        assertThat(fingerprint.teacherOnboarding(new AddTeacherRoleCommand(UUID.randomUUID(), actor, emptyDescription))).isNotEqualTo(value);
    }

    @Test
    void protectsFingerprintWithTheServerKey() {
        RegistrationCommand command = registration("test@example.test", "StrongPassword42!", "Test", Set.of("MATH"));
        byte[] secondKey = new byte[32];
        Arrays.fill(secondKey, (byte) 1);
        assertThat(service(new byte[32]).registration(command)).isNotEqualTo(service(secondKey).registration(command));
    }

    @Test
    void refusesAnInsufficientKey() {
        assertThatThrownBy(() -> service(new byte[31])).isInstanceOf(IllegalArgumentException.class);
    }

    private HmacWorkflowFingerprint service(byte[] key) {
        return new HmacWorkflowFingerprint(new WorkflowFingerprintProperties(new ByteArrayResource(key)), new ObjectMapper());
    }

    private RegistrationCommand registration(String email, String password, String name, Set<String> subjects) {
        return new RegistrationCommand(UUID.randomUUID(), email, password, name, "User", LocalDate.of(2000, 1, 1),
                Set.of(UserRoleView.STUDENT, UserRoleView.TEACHER),
                new TeacherProfileData("Teacher", "CONTACT@EXAMPLE.TEST", List.of(), subjects, null, null, null, null, null),
                new StudentProfileData("Student", "contact@example.test", List.of(), Set.of(), null));
    }

    private TeacherProfileData teacher(List<String> contacts, String description) {
        return new TeacherProfileData("Teacher", "contact@example.test", contacts, Set.of("MATH"), description, null, null, null, null);
    }
}

