package io.github.edtechdevelopment.tutoring.application.profile.model.query;

import io.github.edtechdevelopment.tutoring.application.profile.exception.InvalidPublicProfileCursorException;
import io.github.edtechdevelopment.tutoring.application.profile.exception.InvalidPublicProfileSearchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicProfileSearchQueryTest {

    @ParameterizedTest
    @MethodSource("invalidFilters")
    void rejectsInvalidFilters(String subject, Integer minAge, Integer maxAge, String field) {
        InvalidPublicProfileSearchException error = assertThrows(InvalidPublicProfileSearchException.class,
                () -> new PublicProfileSearchQuery(subject, minAge, maxAge, null));
        assertEquals(Set.of(field), error.invalidFields());
    }

    @Test
    void rejectsBlankCursorInsteadOfStartingAnotherFirstPage() {
        assertThrows(InvalidPublicProfileCursorException.class,
                () -> new PublicProfileSearchQuery(null, null, null, " "));
    }

    private static Stream<Arguments> invalidFilters() {
        return Stream.of(
                Arguments.of("math", null, null, "subjectCode"),
                Arguments.of("", null, null, "subjectCode"),
                Arguments.of(null, -1, null, "minAge"),
                Arguments.of(null, null, -1, "maxAge"),
                Arguments.of(null, 40, 25, "maxAge")
        );
    }
}
