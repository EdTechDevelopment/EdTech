package io.github.edtechdevelopment.tutoring.application.profile.mapper;

import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileEmail;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileViewMapperTest {

    private final ProfileViewMapper mapper = new ProfileViewMapper();

    @Test
    void teacherCardContainsShortDescriptionAndSearchFields() {
        String description = "а".repeat(197) + "🙂" + "ббб";
        TeacherProfile profile = TeacherProfile.create(UUID.randomUUID(), "Teacher",
                new ProfileEmail("teacher@example.com"), List.of("@teacher"), Set.of(new SubjectCode("MATH")),
                description, "Education", 5, "Moscow", URI.create("https://example.com/photo.png"),
                Instant.parse("2026-10-01T00:00:00Z"));

        var card = mapper.toCard(profile, 24);

        assertEquals(profile.userId(), card.userId());
        assertEquals("Teacher", card.displayName());
        assertEquals(24, card.age());
        assertEquals("https://example.com/photo.png", card.photoUrl());
        assertEquals(Set.of("MATH"), card.subjectCodes());
        assertEquals("Moscow", card.city());
        assertTrue(card.descriptionPreview().endsWith("..."));
        assertEquals(200, card.descriptionPreview().codePointCount(0, card.descriptionPreview().length()));
    }
}
