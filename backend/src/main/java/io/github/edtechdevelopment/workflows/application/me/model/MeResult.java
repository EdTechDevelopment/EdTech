package io.github.edtechdevelopment.workflows.application.me.model;

import io.github.edtechdevelopment.identity.api.model.OwnAccountView;
import io.github.edtechdevelopment.tutoring.api.model.profile.StudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.TeacherProfileView;

import java.util.Objects;

public record MeResult(
        OwnAccountView user,
        TeacherProfileView teacherProfile,
        StudentProfileView studentProfile
) {

    public MeResult {
        Objects.requireNonNull(user, "Account must not be null");
    }

    @Override
    public String toString() {
        return "MeResult[PROTECTED]";
    }
}

