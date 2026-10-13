package io.github.edtechdevelopment.tutoring.application.invitation.port.in;

import io.github.edtechdevelopment.tutoring.application.invitation.model.command.CreateStudentInvitationCommand;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.InvitationCreatedResult;

public interface CreateStudentInvitationUseCase {

    InvitationCreatedResult execute(CreateStudentInvitationCommand command);
}
