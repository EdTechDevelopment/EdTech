package io.github.edtechdevelopment.tutoring.presentation.invitation.model;

import java.time.Instant;
import java.util.UUID;
public record IncomingInvitationResponse(UUID invitationId, TeacherSummaryResponse teacher, String status, Instant createdAt, Instant expiresAt, Instant respondedAt) { }
