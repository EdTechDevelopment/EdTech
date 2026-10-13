package io.github.edtechdevelopment.workflows.application.onboarding.port.in;

import io.github.edtechdevelopment.workflows.application.me.model.MeResult;
import io.github.edtechdevelopment.workflows.application.onboarding.model.AddTeacherRoleCommand;

public interface AddTeacherRoleUseCase {

    MeResult addTeacherRole(AddTeacherRoleCommand command);
}

