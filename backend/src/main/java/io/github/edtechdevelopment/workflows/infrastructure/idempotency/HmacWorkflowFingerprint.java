package io.github.edtechdevelopment.workflows.infrastructure.idempotency;

import io.github.edtechdevelopment.tutoring.api.model.profile.input.StudentProfileData;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;
import io.github.edtechdevelopment.workflows.application.idempotency.port.out.WorkflowFingerprint;
import io.github.edtechdevelopment.workflows.application.onboarding.model.AddStudentRoleCommand;
import io.github.edtechdevelopment.workflows.application.onboarding.model.AddTeacherRoleCommand;
import io.github.edtechdevelopment.workflows.application.registration.model.RegistrationCommand;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class HmacWorkflowFingerprint implements WorkflowFingerprint {

    private final SecretKeySpec key;
    private final ObjectMapper json;

    public HmacWorkflowFingerprint(WorkflowFingerprintProperties properties, ObjectMapper json) {
        this.json = json;
        try (InputStream input = properties.fingerprintKey().getInputStream()) {
            byte[] bytes = input.readAllBytes();
            if (bytes.length < 32) {
                throw new IllegalArgumentException("Workflow fingerprint key must contain at least 32 bytes");
            }
            key = new SecretKeySpec(bytes, "HmacSHA256");
        } catch (IOException exception) {
            throw new IllegalStateException("Workflow fingerprint key could not be loaded", exception);
        }
    }

    @Override
    public String registration(RegistrationCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", 1);
        payload.put("operation", "REGISTER");
        payload.put("email", email(command.email()));
        payload.put("password", command.rawPassword());
        payload.put("firstName", command.firstName().strip());
        payload.put("lastName", command.lastName().strip());
        payload.put("birthDate", command.birthDate());
        payload.put("roles", command.roles().stream().map(Enum::name).sorted().toList());
        payload.put("teacherProfile", command.teacherProfile() == null ? null : teacher(command.teacherProfile()));
        payload.put("studentProfile", command.studentProfile() == null ? null : student(command.studentProfile()));
        return sign(payload);
    }

    @Override
    public String teacherOnboarding(AddTeacherRoleCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", 1);
        payload.put("operation", "ADD_TEACHER_ROLE");
        payload.put("userId", command.actorUserId());
        payload.put("profile", teacher(command.profile()));
        return sign(payload);
    }

    @Override
    public String studentOnboarding(AddStudentRoleCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", 1);
        payload.put("operation", "ADD_STUDENT_ROLE");
        payload.put("userId", command.actorUserId());
        payload.put("profile", student(command.profile()));
        return sign(payload);
    }

    private Map<String, Object> teacher(TeacherProfileData profile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("displayName", profile.displayName().strip());
        payload.put("contactEmail", email(profile.contactEmail()));
        payload.put("contactDetails", profile.contactDetails());
        payload.put("subjectCodes", profile.subjectCodes().stream().sorted().toList());
        payload.put("description", profile.description());
        payload.put("education", profile.education());
        payload.put("experienceYears", profile.experienceYears());
        payload.put("city", profile.city());
        payload.put("photoUrl", profile.photoUrl());
        return payload;
    }

    private Map<String, Object> student(StudentProfileData profile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("displayName", profile.displayName().strip());
        payload.put("contactEmail", email(profile.contactEmail()));
        payload.put("contactDetails", profile.contactDetails());
        payload.put("subjectCodes", profile.subjectCodes().stream().sorted().toList());
        payload.put("photoUrl", profile.photoUrl());
        return payload;
    }

    private String email(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private String sign(Map<String, Object> payload) {
        byte[] bytes = json.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(bytes));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Workflow fingerprint calculation failed", exception);
        }
    }
}

