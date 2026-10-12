package io.github.edtechdevelopment.tutoring.application.profile.model.result;

import io.github.edtechdevelopment.tutoring.api.model.profile.search.TeacherProfileCardView;

import java.util.List;
import java.util.Objects;

public record PublicTeacherProfilePageResult(List<TeacherProfileCardView> items, String nextCursor) {

    public PublicTeacherProfilePageResult {
        items = List.copyOf(Objects.requireNonNull(items, "Profile items must not be null"));
    }
}
