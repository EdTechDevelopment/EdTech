package io.github.edtechdevelopment.tutoring.infrastructure.invitation.persistence;

import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationPageCursor;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.PersistenceChecks;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.tables.records.TutoringStudentInvitationsRecord;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.TUTORING_STUDENT_INVITATIONS;
import static org.jooq.impl.DSL.when;

@Repository
public class StudentInvitationJooqRepository {
    private final DSLContext dsl;
    public StudentInvitationJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TutoringStudentInvitationsRecord> findForUpdate(UUID id) {
        return dsl.selectFrom(TUTORING_STUDENT_INVITATIONS).where(TUTORING_STUDENT_INVITATIONS.ID.eq(id))
                .forUpdate().fetchOptional();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<TutoringStudentInvitationsRecord> findPendingForUpdate(String email, UUID teacherId, Instant now) {
        Condition condition = TUTORING_STUDENT_INVITATIONS.STUDENT_EMAIL.eq(email)
                .and(TUTORING_STUDENT_INVITATIONS.STATUS.eq("PENDING"));
        if (teacherId != null) {
            condition = condition.and(TUTORING_STUDENT_INVITATIONS.TEACHER_USER_ID.eq(teacherId));
        }
        if (now != null) {
            condition = condition.and(TUTORING_STUDENT_INVITATIONS.EXPIRES_AT.gt(now.atOffset(ZoneOffset.UTC)));
        }
        return dsl.selectFrom(TUTORING_STUDENT_INVITATIONS).where(condition)
                .orderBy(TUTORING_STUDENT_INVITATIONS.ID).forUpdate().fetch();
    }

    public List<org.jooq.Record> findPage(UUID owner, boolean incoming, String status, Instant asOf, InvitationPageCursor after, int limit) {
        var snapshot = asOf.atOffset(ZoneOffset.UTC);
        Field<String> snapshotStatus = when(TUTORING_STUDENT_INVITATIONS.RESPONDED_AT.le(snapshot), TUTORING_STUDENT_INVITATIONS.STATUS)
                .when(TUTORING_STUDENT_INVITATIONS.EXPIRES_AT.le(snapshot), "EXPIRED").otherwise("PENDING");
        Condition condition = TUTORING_STUDENT_INVITATIONS.CREATED_AT.le(snapshot);
        if (incoming) {
            condition = condition.and(TUTORING_STUDENT_INVITATIONS.STUDENT_USER_ID.eq(owner))
                    .and(TUTORING_STUDENT_INVITATIONS.ATTACHED_AT.le(snapshot));
        } else {
            condition = condition.and(TUTORING_STUDENT_INVITATIONS.TEACHER_USER_ID.eq(owner));
        }
        if (status != null) {
            condition = condition.and(snapshotStatus.eq(status));
        }
        if (after != null) {
            var time = after.createdAt().atOffset(ZoneOffset.UTC);
            condition = condition.and(TUTORING_STUDENT_INVITATIONS.CREATED_AT.lt(time).or(
                    TUTORING_STUDENT_INVITATIONS.CREATED_AT.eq(time).and(TUTORING_STUDENT_INVITATIONS.ID.lt(after.invitationId()))));
        }
        return dsl.select(TUTORING_STUDENT_INVITATIONS.fields()).select(snapshotStatus.as("status_as_of"))
                .from(TUTORING_STUDENT_INVITATIONS).where(condition)
                .orderBy(TUTORING_STUDENT_INVITATIONS.CREATED_AT.desc(), TUTORING_STUDENT_INVITATIONS.ID.desc())
                .limit(limit).fetch();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(TutoringStudentInvitationsRecord row) {
        dsl.insertInto(TUTORING_STUDENT_INVITATIONS).set(row).execute();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateAll(List<TutoringStudentInvitationsRecord> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Query> updates = new ArrayList<>();
        for (var row : rows) {
            updates.add(dsl.update(TUTORING_STUDENT_INVITATIONS)
                    .set(TUTORING_STUDENT_INVITATIONS.STUDENT_USER_ID, row.getStudentUserId())
                    .set(TUTORING_STUDENT_INVITATIONS.ATTACHED_AT, row.getAttachedAt())
                    .set(TUTORING_STUDENT_INVITATIONS.STATUS, row.getStatus())
                    .set(TUTORING_STUDENT_INVITATIONS.RESPONDED_AT, row.getRespondedAt())
                    .where(TUTORING_STUDENT_INVITATIONS.ID.eq(row.getId())));
        }
        for (int count : dsl.batch(updates).execute()) {
            PersistenceChecks.requireOneRow(count);
        }
    }
}
