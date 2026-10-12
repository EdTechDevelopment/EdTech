package io.github.edtechdevelopment.tutoring.application.invitation.service;

import io.github.edtechdevelopment.tutoring.api.exception.ProfileNotFoundException;
import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;
import io.github.edtechdevelopment.tutoring.api.model.profile.summary.TeacherProfileSummary;
import io.github.edtechdevelopment.tutoring.api.query.TutoringProfileQuery;
import io.github.edtechdevelopment.tutoring.application.invitation.mapper.InvitationResultMapper;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationDirection;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationPageCursor;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationPageRow;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.IncomingInvitationsQuery;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.SentInvitationsQuery;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.IncomingInvitationPageResult;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.IncomingInvitationResult;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.SentInvitationPageResult;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.SentInvitationResult;
import io.github.edtechdevelopment.tutoring.application.invitation.port.in.ListIncomingInvitationsUseCase;
import io.github.edtechdevelopment.tutoring.application.invitation.port.in.ListSentInvitationsUseCase;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.InvitationCursorCodec;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.StudentInvitationRepository;
import io.github.edtechdevelopment.tutoring.application.profile.exception.ProfileReadConsistencyException;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class InvitationQueryService implements ListSentInvitationsUseCase, ListIncomingInvitationsUseCase {

    private final TeacherProfileRepository teacherProfiles;
    private final StudentInvitationRepository invitations;
    private final InvitationCursorCodec cursorCodec;
    private final TutoringProfileQuery profiles;
    private final InvitationResultMapper mapper;
    private final Clock clock;

    public InvitationQueryService(
            TeacherProfileRepository teacherProfiles,
            StudentInvitationRepository invitations,
            InvitationCursorCodec cursorCodec,
            TutoringProfileQuery profiles,
            InvitationResultMapper mapper,
            Clock clock) {

        this.teacherProfiles = Objects.requireNonNull(teacherProfiles, "Teacher profile repository must not be null");
        this.invitations = Objects.requireNonNull(invitations, "Invitation repository must not be null");
        this.cursorCodec = Objects.requireNonNull(cursorCodec, "Invitation cursor codec must not be null");
        this.profiles = Objects.requireNonNull(profiles, "Tutoring profile query must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Invitation result mapper must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public SentInvitationPageResult execute(SentInvitationsQuery query) {
        Objects.requireNonNull(query, "Sent invitations query must not be null");
        if (!teacherProfiles.existsByUserId(query.teacherUserId())) {
            throw new ProfileNotFoundException(ProfileTypeView.TEACHER, query.teacherUserId());
        }

        InvitationDirection direction = InvitationDirection.SENT;
        InvitationPageCursor after = decodeCursor(
                query.cursor(), query.teacherUserId(), direction, query.status());
        Instant asOf = after == null ? clock.instant() : after.asOf();
        List<InvitationPageRow> rows = new ArrayList<>(invitations.findSentPage(
                query.teacherUserId(), query.status(), asOf, after, query.limit() + 1));

        String nextCursor = removeLookaheadAndCreateCursor(
                rows, query.limit(), query.teacherUserId(), direction, query.status(), asOf);
        List<SentInvitationResult> items = new ArrayList<>(rows.size());
        for (InvitationPageRow row : rows) {
            items.add(mapper.toSentResult(row));
        }
        return new SentInvitationPageResult(items, nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public IncomingInvitationPageResult execute(IncomingInvitationsQuery query) {
        Objects.requireNonNull(query, "Incoming invitations query must not be null");

        InvitationDirection direction = InvitationDirection.INCOMING;
        InvitationPageCursor after = decodeCursor(
                query.cursor(), query.studentUserId(), direction, query.status());
        Instant asOf = after == null ? clock.instant() : after.asOf();
        List<InvitationPageRow> rows = new ArrayList<>(invitations.findIncomingPage(
                query.studentUserId(), query.status(), asOf, after, query.limit() + 1));

        String nextCursor = removeLookaheadAndCreateCursor(
                rows, query.limit(), query.studentUserId(), direction, query.status(), asOf);
        Set<UUID> teacherIds = new HashSet<>();
        for (InvitationPageRow row : rows) {
            teacherIds.add(row.invitation().teacherUserId());
        }
        Map<UUID, TeacherProfileSummary> summaries = profiles.findTeacherSummaries(Set.copyOf(teacherIds));
        List<IncomingInvitationResult> items = new ArrayList<>(rows.size());
        for (InvitationPageRow row : rows) {
            UUID teacherId = row.invitation().teacherUserId();
            TeacherProfileSummary summary = summaries.get(teacherId);
            if (summary == null) {
                throw new ProfileReadConsistencyException(teacherId, "Invitation sender profile is missing");
            }
            items.add(mapper.toIncomingResult(row, summary));
        }
        return new IncomingInvitationPageResult(items, nextCursor);
    }

    private InvitationPageCursor decodeCursor(
            String cursor, UUID actorUserId, InvitationDirection direction, InvitationStatus status) {

        return cursor == null ? null : cursorCodec.decode(cursor, actorUserId, direction, status);
    }

    private String removeLookaheadAndCreateCursor(
            List<InvitationPageRow> rows, int limit, UUID actorUserId,
            InvitationDirection direction, InvitationStatus status, Instant asOf) {

        if (rows.size() <= limit) {
            return null;
        }
        rows.removeLast();
        InvitationPageRow last = rows.getLast();
        InvitationPageCursor position = new InvitationPageCursor(
                last.invitation().createdAt(), last.invitation().id(), asOf);
        return cursorCodec.encode(position, actorUserId, direction, status);
    }
}
