package io.github.edtechdevelopment.tutoring.application.invitation.port.in;

import io.github.edtechdevelopment.tutoring.application.invitation.model.query.SentInvitationsQuery;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.SentInvitationPageResult;

public interface ListSentInvitationsUseCase {

    SentInvitationPageResult execute(SentInvitationsQuery query);
}
