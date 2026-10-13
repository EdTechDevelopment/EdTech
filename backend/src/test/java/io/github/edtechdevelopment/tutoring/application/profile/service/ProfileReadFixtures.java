package io.github.edtechdevelopment.tutoring.application.profile.service;

import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileEmail;
import io.github.edtechdevelopment.tutoring.domain.profile.model.StudentProfile;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ProfileReadFixtures {

    static final Instant NOW = Instant.parse("2026-10-09T23:30:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.ofHours(3));

    private ProfileReadFixtures() {
    }

    static UUID id(int number) {
        return new UUID(0, number);
    }

    static TeacherProfile teacher(UUID userId) {
        return TeacherProfile.create(userId, "Teacher", new ProfileEmail("contact@example.com"),
                List.of("@teacher"), Set.of(new SubjectCode("MATH")), "Description", "Education", 0,
                "City", URI.create("https://example.com/photo.png"), NOW);
    }

    static StudentProfile student(UUID userId) {
        return StudentProfile.create(userId, "Student", new ProfileEmail("student@example.com"),
                List.of("@student"), Set.of(), null, NOW);
    }
}
