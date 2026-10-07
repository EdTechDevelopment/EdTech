package io.github.edtechdevelopment.tutoring.domain.profile.model;

import io.github.edtechdevelopment.tutoring.domain.profile.exception.EmptyTeacherSubjectsException;
import io.github.edtechdevelopment.tutoring.domain.profile.exception.InvalidExperienceException;
import io.github.edtechdevelopment.tutoring.domain.profile.exception.InvalidProfileEmailException;
import io.github.edtechdevelopment.tutoring.domain.profile.exception.InvalidProfileNameException;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileTest {

    private static final SubjectCode MATH = new SubjectCode("MATH");

    @Test
    void teacherRequiresSubjectsButStudentMayClearThem() {
        assertThrows(EmptyTeacherSubjectsException.class, () -> teacher(Set.of()));

        TeacherProfile teacher = teacher(Set.of(MATH));
        assertThrows(EmptyTeacherSubjectsException.class, () -> teacher.changeSubjects(Set.of()));
        assertEquals(Set.of(MATH), teacher.subjectCodes());

        StudentProfile student = student(Set.of(MATH));
        student.changeSubjects(Set.of());
        assertTrue(student.subjectCodes().isEmpty());
    }

    @Test
    void profilesKeepCopiesOfCollections() {
        List<String> contacts = new ArrayList<>(List.of("telegram"));
        Set<SubjectCode> subjects = new HashSet<>(Set.of(MATH));
        TeacherProfile teacher = TeacherProfile.create(UUID.randomUUID(), " Teacher ", new ProfileEmail("a@example.com"),
                contacts, subjects, null, null, null, null, null);

        contacts.add("phone");
        subjects.clear();

        assertEquals("Teacher", teacher.displayName());
        assertEquals(List.of("telegram"), teacher.contactDetails());
        assertEquals(Set.of(MATH), teacher.subjectCodes());
        assertThrows(UnsupportedOperationException.class, () -> teacher.contactDetails().add("other"));
        assertThrows(UnsupportedOperationException.class, () -> teacher.subjectCodes().clear());
    }

    @Test
    void emailChangeIsImmediateAndIdempotent() {
        StudentProfile student = student(Set.of());

        assertFalse(student.changeContactEmail(new ProfileEmail("  STUDENT@example.COM ")));
        assertTrue(student.changeContactEmail(new ProfileEmail("new@example.com")));
        assertEquals("new@example.com", student.contactEmail().value());
        assertThrows(InvalidProfileEmailException.class, () -> new ProfileEmail("bad@@example.com"));
    }

    @Test
    void invalidUpdateDoesNotChangeTeacher() {
        TeacherProfile teacher = teacher(Set.of(MATH));

        assertThrows(InvalidExperienceException.class,
                () -> teacher.updateDetails("New name", List.of("phone"), null, null, -1, null, null));
        assertEquals("Teacher", teacher.displayName());
        assertEquals(List.of(), teacher.contactDetails());
        assertThrows(InvalidProfileNameException.class,
                () -> teacher.updateDetails("  ", List.of(), null, null, 0, null, null));
    }

    private static TeacherProfile teacher(Set<SubjectCode> subjects) {
        return TeacherProfile.create(UUID.randomUUID(), "Teacher", new ProfileEmail("teacher@example.com"),
                List.of(), subjects, null, null, null, null, null);
    }

    private static StudentProfile student(Set<SubjectCode> subjects) {
        return StudentProfile.create(UUID.randomUUID(), "Student", new ProfileEmail("student@example.com"),
                List.of(), subjects, null);
    }
}
