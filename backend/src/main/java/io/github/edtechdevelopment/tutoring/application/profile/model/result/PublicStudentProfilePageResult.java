package io.github.edtechdevelopment.tutoring.application.profile.model.result;

import io.github.edtechdevelopment.tutoring.api.model.profile.search.StudentProfileCardView;

import java.util.List;
import java.util.Objects;

public record PublicStudentProfilePageResult(List<StudentProfileCardView> items, String nextCursor) {

    public PublicStudentProfilePageResult {
        items = List.copyOf(Objects.requireNonNull(items, "Profile items must not be null"));
    }
}
