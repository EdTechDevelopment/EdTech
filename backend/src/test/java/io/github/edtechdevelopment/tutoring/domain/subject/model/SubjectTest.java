package io.github.edtechdevelopment.tutoring.domain.subject.model;

import io.github.edtechdevelopment.tutoring.domain.subject.exception.InvalidSubjectCodeException;
import io.github.edtechdevelopment.tutoring.domain.subject.exception.InvalidSubjectNameException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubjectTest {

    @Test
    void acceptsStableCodeAndNormalizesName() {
        Subject subject = new Subject(new SubjectCode("MATH_1"), "  Mathematics  ");

        assertEquals("MATH_1", subject.code().value());
        assertEquals("Mathematics", subject.name());
    }

    @Test
    void rejectsInvalidCodeAndBlankName() {
        assertThrows(InvalidSubjectCodeException.class, () -> new SubjectCode("math"));
        assertThrows(InvalidSubjectCodeException.class, () -> new SubjectCode("1MATH"));
        assertThrows(InvalidSubjectCodeException.class, () -> new SubjectCode("A".repeat(33)));
        assertThrows(InvalidSubjectNameException.class, () -> new Subject(new SubjectCode("MATH"), "   "));
    }
}
