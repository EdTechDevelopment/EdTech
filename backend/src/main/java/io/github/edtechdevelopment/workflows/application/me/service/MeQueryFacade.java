package io.github.edtechdevelopment.workflows.application.me.service;

import io.github.edtechdevelopment.identity.api.model.OwnAccountView;
import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.identity.api.query.OwnAccountQuery;
import io.github.edtechdevelopment.tutoring.api.model.profile.StudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.TeacherProfileView;
import io.github.edtechdevelopment.tutoring.api.query.TutoringProfileQuery;
import io.github.edtechdevelopment.workflows.application.exception.WorkflowException;
import io.github.edtechdevelopment.workflows.application.me.model.MeResult;
import io.github.edtechdevelopment.workflows.application.me.port.in.GetMeUseCase;

import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

public class MeQueryFacade implements GetMeUseCase {

    private final OwnAccountQuery accounts;
    private final TutoringProfileQuery profiles;

    public MeQueryFacade(OwnAccountQuery accounts, TutoringProfileQuery profiles) {
        this.accounts = Objects.requireNonNull(accounts, "Account query must not be null");
        this.profiles = Objects.requireNonNull(profiles, "Profile query must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public MeResult getMe(UUID actorUserId) {
        Objects.requireNonNull(actorUserId, "Actor user id must not be null");
        OwnAccountView account = accounts.findAccountForOwner(actorUserId)
                .orElseThrow(() -> new WorkflowException(WorkflowException.Reason.ACCOUNT_UNAVAILABLE));
        TeacherProfileView teacher = null;
        StudentProfileView student = null;
        if (account.roles().contains(UserRoleView.TEACHER)) {
            teacher = profiles.findTeacherProfile(actorUserId)
                    .orElseThrow(() -> new WorkflowException(WorkflowException.Reason.PROFILE_INCONSISTENCY));
        }
        if (account.roles().contains(UserRoleView.STUDENT)) {
            student = profiles.findStudentProfile(actorUserId)
                    .orElseThrow(() -> new WorkflowException(WorkflowException.Reason.PROFILE_INCONSISTENCY));
        }
        return new MeResult(account, teacher, student);
    }
}

