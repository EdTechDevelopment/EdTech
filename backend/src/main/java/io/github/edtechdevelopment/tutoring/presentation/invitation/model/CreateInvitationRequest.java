package io.github.edtechdevelopment.tutoring.presentation.invitation.model;

import jakarta.validation.constraints.NotBlank;

public record CreateInvitationRequest(@NotBlank String studentEmail) {
    @Override
    public String toString() {
        return "CreateInvitationRequest[PROTECTED]";
    }
}
