package io.github.edtechdevelopment.tutoring.presentation.invitation.model;

import java.util.UUID;
public record InvitationRejectedResponse(UUID invitationId, String status) { }
