package io.github.edtechdevelopment.tutoring.application.invitation.port.in;

import io.github.edtechdevelopment.tutoring.application.invitation.model.query.IncomingInvitationsQuery;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.IncomingInvitationPageResult;

public interface ListIncomingInvitationsUseCase {

    IncomingInvitationPageResult execute(IncomingInvitationsQuery query);
}
