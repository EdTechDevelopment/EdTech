package io.github.edtechdevelopment.tutoring.application.invitation.model.result;

import java.util.List;
import java.util.Objects;

public record SentInvitationPageResult(List<SentInvitationResult> items, String nextCursor) {

    public SentInvitationPageResult {
        items = List.copyOf(Objects.requireNonNull(items, "Invitation items must not be null"));
    }
}
