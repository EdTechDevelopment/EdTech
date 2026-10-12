package io.github.edtechdevelopment.tutoring.application.invitation.model.result;

import java.util.List;
import java.util.Objects;

public record IncomingInvitationPageResult(List<IncomingInvitationResult> items, String nextCursor) {

    public IncomingInvitationPageResult {
        items = List.copyOf(Objects.requireNonNull(items, "Invitation items must not be null"));
    }
}
