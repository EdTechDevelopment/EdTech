package io.github.edtechdevelopment.tutoring.presentation.invitation.model;

import java.time.Instant;
import java.util.UUID;
public record InvitationCreatedResponse(UUID invitationId, Instant expiresAt) { }
