package io.github.edtechdevelopment.tutoring.application.profile.model.query;

import io.github.edtechdevelopment.tutoring.application.profile.exception.InvalidPublicProfileCursorException;
import io.github.edtechdevelopment.tutoring.application.profile.exception.InvalidPublicProfileSearchException;
import io.github.edtechdevelopment.tutoring.domain.subject.exception.InvalidSubjectCodeException;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;

public record PublicProfileSearchQuery(
        String subjectCode,
        Integer minAge,
        Integer maxAge,
        String cursor
) {

    public PublicProfileSearchQuery {
        if (subjectCode != null) {
            try {
                new SubjectCode(subjectCode);
            } catch (InvalidSubjectCodeException ignored) {
                throw new InvalidPublicProfileSearchException("subjectCode");
            }
        }
        if (minAge != null && minAge < 0) {
            throw new InvalidPublicProfileSearchException("minAge");
        }
        if (maxAge != null && (maxAge < 0 || (minAge != null && maxAge < minAge))) {
            throw new InvalidPublicProfileSearchException("maxAge");
        }
        if (cursor != null && cursor.isBlank()) {
            throw new InvalidPublicProfileCursorException();
        }
    }
}
