package io.github.edtechdevelopment.tutoring.application.subject.service;

import io.github.edtechdevelopment.tutoring.application.subject.exception.SubjectNotFoundException;
import io.github.edtechdevelopment.tutoring.application.subject.mapper.SubjectResultMapper;
import io.github.edtechdevelopment.tutoring.application.subject.model.result.SubjectResult;
import io.github.edtechdevelopment.tutoring.application.subject.port.out.SubjectRepository;
import io.github.edtechdevelopment.tutoring.domain.subject.model.Subject;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetSubjectsServiceTest {

    private final SubjectRepository subjectRepository = mock(SubjectRepository.class);
    private final GetSubjectsService service = new GetSubjectsService(subjectRepository, new SubjectResultMapper());

    @Test
    void returnsSortedImmutableCatalog() {
        when(subjectRepository.findAll()).thenReturn(List.of(
                new Subject(new SubjectCode("PHYSICS"), "Physics"),
                new Subject(new SubjectCode("CHEMISTRY"), "Chemistry"),
                new Subject(new SubjectCode("MATH"), "Mathematics")
        ));

        List<SubjectResult> results = service.getSubjects();

        assertEquals(List.of(
                new SubjectResult("CHEMISTRY", "Chemistry"),
                new SubjectResult("MATH", "Mathematics"),
                new SubjectResult("PHYSICS", "Physics")
        ), results);
        assertThrows(UnsupportedOperationException.class,
                () -> results.add(new SubjectResult("OTHER", "Other")));
    }

    @Test
    void rejectsEmptyCatalog() {
        when(subjectRepository.findAll()).thenReturn(List.of());

        assertThrows(SubjectNotFoundException.class, service::getSubjects);
    }
}
