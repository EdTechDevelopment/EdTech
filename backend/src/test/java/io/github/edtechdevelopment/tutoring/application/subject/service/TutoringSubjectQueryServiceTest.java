package io.github.edtechdevelopment.tutoring.application.subject.service;

import io.github.edtechdevelopment.tutoring.api.exception.UnknownSubjectsException;
import io.github.edtechdevelopment.tutoring.application.subject.port.out.SubjectRepository;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TutoringSubjectQueryServiceTest {

    private final SubjectRepository subjectRepository = mock(SubjectRepository.class);
    private final TutoringSubjectQueryService service = new TutoringSubjectQueryService(subjectRepository);

    @Test
    void checksOneCodeAndDistinguishesMissingCode() {
        SubjectCode math = new SubjectCode("MATH");
        SubjectCode physics = new SubjectCode("PHYSICS");
        when(subjectRepository.exists(math)).thenReturn(true);

        assertTrue(service.exists("MATH"));
        assertFalse(service.exists("PHYSICS"));
        verify(subjectRepository).exists(math);
        verify(subjectRepository).exists(physics);
    }

    @Test
    void rejectsInvalidCodeBeforeRepositoryCall() {
        assertThrows(IllegalArgumentException.class, () -> service.exists("math"));
        assertThrows(IllegalArgumentException.class, () -> service.exists(null));

        verifyNoInteractions(subjectRepository);
    }

    @Test
    void reportsAllUnknownCodesAfterOneBatchLookup() {
        Set<SubjectCode> requested = Set.of(
                new SubjectCode("MATH"), new SubjectCode("PHYSICS"), new SubjectCode("ENGLISH")
        );
        when(subjectRepository.findExistingCodes(requested)).thenReturn(Set.of(new SubjectCode("MATH")));

        UnknownSubjectsException exception = assertThrows(UnknownSubjectsException.class,
                () -> service.requireAllExist(Set.of("MATH", "PHYSICS", "ENGLISH")));

        assertEquals(Set.of("PHYSICS", "ENGLISH"), exception.unknownCodes());
        verify(subjectRepository).findExistingCodes(requested);
    }

    @Test
    void acceptsBatchWhenEveryCodeExists() {
        Set<SubjectCode> requested = Set.of(new SubjectCode("MATH"), new SubjectCode("PHYSICS"));
        when(subjectRepository.findExistingCodes(requested)).thenReturn(requested);

        service.requireAllExist(Set.of("MATH", "PHYSICS"));

        verify(subjectRepository).findExistingCodes(requested);
    }

    @Test
    void rejectsInvalidBatchBeforeRepositoryCall() {
        Set<String> withNull = new HashSet<>();
        withNull.add("MATH");
        withNull.add(null);

        assertThrows(IllegalArgumentException.class, () -> service.requireAllExist(Set.of()));
        assertThrows(IllegalArgumentException.class, () -> service.requireAllExist(null));
        assertThrows(IllegalArgumentException.class, () -> service.requireAllExist(withNull));

        verifyNoInteractions(subjectRepository);
    }
}
