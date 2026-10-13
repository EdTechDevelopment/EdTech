package io.github.edtechdevelopment.tutoring.application.invitation.port.in;

import io.github.edtechdevelopment.tutoring.application.invitation.model.command.AcceptStudentInvitationCommand;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.InvitationAcceptedResult;

public interface AcceptStudentInvitationUseCase {

    InvitationAcceptedResult execute(AcceptStudentInvitationCommand command);
}
