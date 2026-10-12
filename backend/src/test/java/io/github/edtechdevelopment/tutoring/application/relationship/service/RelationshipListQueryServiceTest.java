package io.github.edtechdevelopment.tutoring.application.relationship.service;

import io.github.edtechdevelopment.tutoring.api.model.profile.StudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.TeacherProfileView;
import io.github.edtechdevelopment.tutoring.api.query.TutoringProfileQuery;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.service.LinkedProfileProjectionService;
import io.github.edtechdevelopment.tutoring.application.relationship.mapper.RelationshipResultMapper;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.ListStudentTeachersQuery;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.ListTeacherStudentsQuery;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.RelationshipDirection;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.RelationshipPageCursor;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.RelationshipCursorCodec;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import io.github.edtechdevelopment.tutoring.domain.relationship.model.TeacherStudent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelationshipListQueryServiceTest {

    private static final UUID TEACHER_ID = UUID.randomUUID();
    private static final UUID HIDDEN_STUDENT_ID = UUID.randomUUID();
    private static final UUID RETURNED_STUDENT_ID = UUID.randomUUID();
    private static final UUID LOOKAHEAD_STUDENT_ID = UUID.randomUUID();
    private static final Instant NEWEST = Instant.parse("2026-10-12T10:00:00Z");
    private static final Instant MIDDLE = NEWEST.minusSeconds(60);
    private static final Instant OLDEST = NEWEST.minusSeconds(120);

    private final TeacherStudentRepository relationships = mock(TeacherStudentRepository.class);
    private final StudentProfileRepository studentProfiles = mock(StudentProfileRepository.class);
    private final LinkedProfileProjectionService linkedProfiles = mock(LinkedProfileProjectionService.class);
    private final RelationshipCursorCodec cursorCodec = mock(RelationshipCursorCodec.class);
    private final TutoringProfileQuery profileQuery = mock(TutoringProfileQuery.class);
    private final RelationshipListQueryService service = new RelationshipListQueryService(
            studentProfiles, relationships, linkedProfiles, cursorCodec, new RelationshipResultMapper(), profileQuery);

    @Test
    void continuesPastHiddenProfilesAndUsesLastReturnedRelationshipAsCursor() {
        TeacherStudent hidden = TeacherStudent.create(TEACHER_ID, HIDDEN_STUDENT_ID, NEWEST);
        TeacherStudent returned = TeacherStudent.create(TEACHER_ID, RETURNED_STUDENT_ID, MIDDLE);
        TeacherStudent lookahead = TeacherStudent.create(TEACHER_ID, LOOKAHEAD_STUDENT_ID, OLDEST);
        RelationshipPageCursor afterReturned = new RelationshipPageCursor(MIDDLE, RETURNED_STUDENT_ID);
        StudentProfileView returnedProfile = studentProfile(RETURNED_STUDENT_ID);
        StudentProfileView lookaheadProfile = studentProfile(LOOKAHEAD_STUDENT_ID);

        when(relationships.findByTeacher(TEACHER_ID, null, 2)).thenReturn(List.of(hidden, returned));
        when(linkedProfiles.findStudents(Set.of(HIDDEN_STUDENT_ID, RETURNED_STUDENT_ID)))
                .thenReturn(Map.of(RETURNED_STUDENT_ID, returnedProfile));
        when(relationships.findByTeacher(TEACHER_ID, afterReturned, 2)).thenReturn(List.of(lookahead));
        when(linkedProfiles.findStudents(Set.of(LOOKAHEAD_STUDENT_ID)))
                .thenReturn(Map.of(LOOKAHEAD_STUDENT_ID, lookaheadProfile));
        when(cursorCodec.encode(afterReturned, TEACHER_ID, RelationshipDirection.TEACHER_STUDENTS))
                .thenReturn("next-page");

        var page = service.execute(new ListTeacherStudentsQuery(TEACHER_ID, null, 1));

        assertEquals(1, page.items().size());
        assertEquals(RETURNED_STUDENT_ID, page.items().getFirst().student().userId());
        assertEquals("next-page", page.nextCursor());
        verify(relationships).findByTeacher(TEACHER_ID, afterReturned, 2);
    }

    @Test
    void listsTeachersForStudent() {
        TeacherStudent relationship = TeacherStudent.create(TEACHER_ID, RETURNED_STUDENT_ID, NEWEST);
        TeacherProfileView teacher = new TeacherProfileView(
                TEACHER_ID, 30, "Teacher", "teacher@example.com", List.of(), Set.of(),
                "Description", "Education", 5, "City", null);
        when(studentProfiles.existsByUserId(RETURNED_STUDENT_ID)).thenReturn(true);
        when(relationships.findByStudent(RETURNED_STUDENT_ID, null, 11)).thenReturn(List.of(relationship));
        when(linkedProfiles.findTeachers(Set.of(TEACHER_ID))).thenReturn(Map.of(TEACHER_ID, teacher));

        var page = service.execute(new ListStudentTeachersQuery(RETURNED_STUDENT_ID, null, 10));

        assertEquals(1, page.items().size());
        assertEquals(TEACHER_ID, page.items().getFirst().teacher().userId());
        verify(studentProfiles).existsByUserId(RETURNED_STUDENT_ID);
        verify(relationships).findByStudent(RETURNED_STUDENT_ID, null, 11);
    }

    private static StudentProfileView studentProfile(UUID userId) {
        return new StudentProfileView(userId, 18, "Student", "student@example.com", List.of(), Set.of(), null);
    }
}
