package io.github.edtechdevelopment.tutoring.application.profile.service;

import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.profile.exception.ProfileReadConsistencyException;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileViewMapper;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.CLOCK;
import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.TODAY;
import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.id;
import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.student;
import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.teacher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LinkedProfileProjectionServiceTest {

    private final TeacherProfileRepository teachers = mock(TeacherProfileRepository.class);
    private final StudentProfileRepository students = mock(StudentProfileRepository.class);
    private final IdentityAccountGateway identity = mock(IdentityAccountGateway.class);
    private final LinkedProfileProjectionService service = new LinkedProfileProjectionService(
            teachers, students, identity, new ProfileViewMapper(), CLOCK);

    @Test
    void studentBatchUsesOneLookupAndHidesInactiveAccounts() {
        var ids = Set.of(id(1), id(2));
        when(students.findByUserIds(ids)).thenReturn(Map.of(id(1), student(id(1)), id(2), student(id(2))));
        when(identity.findActiveUserIds(ids)).thenReturn(Set.of(id(1)));
        when(identity.findAgesByIds(Set.of(id(1)), TODAY)).thenReturn(Map.of(id(1), 19));

        var views = service.findStudents(ids);

        assertEquals(Set.of(id(1)), views.keySet());
        assertEquals(19, views.get(id(1)).age());
        verify(students).findByUserIds(ids);
        verify(identity).findActiveUserIds(ids);
        verify(identity).findAgesByIds(Set.of(id(1)), TODAY);
        verifyNoInteractions(teachers);
        assertThrows(UnsupportedOperationException.class, views::clear);
    }

    @Test
    void teacherBatchReturnsBothCardsWithoutIndividualRequests() {
        var ids = Set.of(id(1), id(2));
        when(teachers.findByUserIds(ids)).thenReturn(Map.of(id(1), teacher(id(1)), id(2), teacher(id(2))));
        when(identity.findActiveUserIds(ids)).thenReturn(ids);
        when(identity.findAgesByIds(ids, TODAY)).thenReturn(Map.of(id(1), 29, id(2), 40));

        var views = service.findTeachers(ids);

        assertEquals(29, views.get(id(1)).age());
        assertEquals(40, views.get(id(2)).age());
        verify(teachers).findByUserIds(ids);
        verify(identity).findAgesByIds(ids, TODAY);
        verify(teachers, never()).findByUserId(any());
    }

    @Test
    void emptyBatchesDoNotCallDependencies() {
        assertEquals(Map.of(), service.findTeachers(Set.of()));
        assertEquals(Map.of(), service.findStudents(Set.of()));
        verifyNoInteractions(teachers, students, identity);
    }

    @Test
    void missingLinkedProfileIsNotSilentlyOmitted() {
        when(students.findByUserIds(Set.of(id(1), id(2)))).thenReturn(Map.of(id(1), student(id(1))));
        assertThrows(ProfileReadConsistencyException.class, () -> service.findStudents(Set.of(id(1), id(2))));
        verifyNoInteractions(identity);
    }

    @Test
    void allInactiveAccountsDoNotNeedAgeLookup() {
        when(teachers.findByUserIds(Set.of(id(1)))).thenReturn(Map.of(id(1), teacher(id(1))));
        assertEquals(Map.of(), service.findTeachers(Set.of(id(1))));
        verify(identity, never()).findAgesByIds(any(), any());
    }

    @Test
    void missingAgeForActiveAccountIsAnError() {
        when(students.findByUserIds(Set.of(id(1)))).thenReturn(Map.of(id(1), student(id(1))));
        when(identity.findActiveUserIds(Set.of(id(1)))).thenReturn(Set.of(id(1)));
        assertThrows(ProfileReadConsistencyException.class, () -> service.findStudents(Set.of(id(1))));
    }
}
