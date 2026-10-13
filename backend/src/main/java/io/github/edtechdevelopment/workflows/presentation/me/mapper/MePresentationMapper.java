package io.github.edtechdevelopment.workflows.presentation.me.mapper;

import io.github.edtechdevelopment.tutoring.api.model.profile.StudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.TeacherProfileView;
import io.github.edtechdevelopment.workflows.application.me.model.MeResult;
import io.github.edtechdevelopment.workflows.presentation.me.model.MeResponse;
import io.github.edtechdevelopment.workflows.presentation.me.model.OwnAccountResponse;
import io.github.edtechdevelopment.workflows.presentation.me.model.StudentProfileResponse;
import io.github.edtechdevelopment.workflows.presentation.me.model.TeacherProfileResponse;

import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class MePresentationMapper {

    public MeResponse toResponse(MeResult result) {
        var account = result.user();
        OwnAccountResponse user = new OwnAccountResponse(account.id(), account.email(), account.pendingEmail(),
                account.firstName(), account.lastName(), account.birthDate(),
                account.roles().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()),
                account.status().name(), account.emailVerifiedAt(), account.createdAt(), account.updatedAt());
        return new MeResponse(user, result.teacherProfile() == null ? null : teacher(result.teacherProfile()),
                result.studentProfile() == null ? null : student(result.studentProfile()));
    }

    private TeacherProfileResponse teacher(TeacherProfileView profile) {
        return new TeacherProfileResponse(profile.userId(), profile.age(), profile.displayName(), profile.contactEmail(),
                profile.contactDetails(), profile.subjectCodes(), profile.description(), profile.education(),
                profile.experienceYears(), profile.city(), profile.photoUrl());
    }

    private StudentProfileResponse student(StudentProfileView profile) {
        return new StudentProfileResponse(profile.userId(), profile.age(), profile.displayName(), profile.contactEmail(),
                profile.contactDetails(), profile.subjectCodes(), profile.photoUrl());
    }
}

