package io.github.edtechdevelopment.tutoring.infrastructure.cursor;

import io.github.edtechdevelopment.tutoring.application.invitation.exception.InvalidInvitationCursorException;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationDirection;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationPageCursor;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.InvitationCursorCodec;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class HmacInvitationCursorCodec implements InvitationCursorCodec {
    private final SignedCursor signing;
    public HmacInvitationCursorCodec(SignedCursor signing) {
        this.signing = signing;
    }

    private String context(UUID owner, InvitationDirection direction, InvitationStatus status) {
        return owner + "|" + direction.name() + "|" + (status == null ? "" : status.name());
    }
    @Override
    public String encode(InvitationPageCursor cursor, UUID owner, InvitationDirection direction, InvitationStatus status) {
        return signing.encode("invitations|1|" + context(owner, direction, status) + "|" + cursor.createdAt() + "|" + cursor.invitationId() + "|" + cursor.asOf());
    }
    @Override
    public InvitationPageCursor decode(String cursor, UUID owner, InvitationDirection direction, InvitationStatus status) {
        try {
            String[] fields = signing.decode(cursor, 8);
            if (!fields[0].equals("invitations") || !(fields[2] + "|" + fields[3] + "|" + fields[4]).equals(context(owner, direction, status))) {
                throw new InvalidInvitationCursorException();
            }
            return new InvitationPageCursor(Instant.parse(fields[5]), UUID.fromString(fields[6]), Instant.parse(fields[7]));
        } catch (RuntimeException exception) {
            throw new InvalidInvitationCursorException();
        }
    }
}
