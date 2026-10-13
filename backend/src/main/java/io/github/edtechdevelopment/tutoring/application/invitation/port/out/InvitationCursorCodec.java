package io.github.edtechdevelopment.tutoring.application.invitation.port.out;

import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationDirection;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationPageCursor;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;

import java.util.UUID;

public interface InvitationCursorCodec {

    InvitationPageCursor decode(
            String encodedCursor, UUID ownerUserId, InvitationDirection direction, InvitationStatus status);

    String encode(
            InvitationPageCursor cursor, UUID ownerUserId, InvitationDirection direction, InvitationStatus status);
}
