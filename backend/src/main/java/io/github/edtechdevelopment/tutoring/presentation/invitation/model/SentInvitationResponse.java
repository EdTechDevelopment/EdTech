package io.github.edtechdevelopment.tutoring.presentation.invitation.model;

import java.time.Instant;
import java.util.UUID;
public record SentInvitationResponse(UUID invitationId, String studentEmail, String status, Instant createdAt, Instant expiresAt, Instant respondedAt) { }
