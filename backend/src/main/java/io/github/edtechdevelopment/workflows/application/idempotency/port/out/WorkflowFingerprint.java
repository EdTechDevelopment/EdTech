package io.github.edtechdevelopment.workflows.application.idempotency.port.out;

import io.github.edtechdevelopment.workflows.application.onboarding.model.AddStudentRoleCommand;
import io.github.edtechdevelopment.workflows.application.onboarding.model.AddTeacherRoleCommand;
import io.github.edtechdevelopment.workflows.application.registration.model.RegistrationCommand;

public interface WorkflowFingerprint {

    String registration(RegistrationCommand command);

    String teacherOnboarding(AddTeacherRoleCommand command);

    String studentOnboarding(AddStudentRoleCommand command);
}

