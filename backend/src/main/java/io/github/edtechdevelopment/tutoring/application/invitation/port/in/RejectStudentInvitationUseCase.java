package io.github.edtechdevelopment.tutoring.application.invitation.port.in;

import io.github.edtechdevelopment.tutoring.application.invitation.model.command.RejectStudentInvitationCommand;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.InvitationRejectedResult;

public interface RejectStudentInvitationUseCase {

    InvitationRejectedResult execute(RejectStudentInvitationCommand command);
}
