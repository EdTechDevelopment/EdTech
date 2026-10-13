package io.github.edtechdevelopment.tutoring.infrastructure.invitation.persistence;

import io.github.edtechdevelopment.tutoring.application.invitation.exception.InvitationCreationConflictException;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationPageCursor;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationPageRow;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.StudentInvitationRepository;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationEmail;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.StudentInvitation;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.PersistenceChecks;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.TUTORING_STUDENT_INVITATIONS;

@Repository
public class JooqStudentInvitationRepositoryAdapter implements StudentInvitationRepository {
    private final StudentInvitationJooqRepository repository;
    private final StudentInvitationPersistenceMapper mapper;
    public JooqStudentInvitationRepositoryAdapter(StudentInvitationJooqRepository repository, StudentInvitationPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }
    @Override
    public Optional<StudentInvitation> findByIdForUpdate(UUID id) {
        return repository.findForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public List<StudentInvitation> findPendingByTeacherAndEmailForUpdate(UUID teacher, InvitationEmail email) {
        return repository.findPendingForUpdate(email.value(), teacher, null).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<StudentInvitation> findPendingByEmailForUpdate(InvitationEmail email, Instant now) {
        return repository.findPendingForUpdate(email.value(), null, now).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<InvitationPageRow> findSentPage(UUID owner, InvitationStatus status, Instant asOf, InvitationPageCursor after, int limit) {
        return findPage(owner, false, status, asOf, after, limit);
    }
    @Override
    public List<InvitationPageRow> findIncomingPage(UUID owner, InvitationStatus status, Instant asOf, InvitationPageCursor after, int limit) {
        return findPage(owner, true, status, asOf, after, limit);
    }
    private List<InvitationPageRow> findPage(UUID owner, boolean incoming, InvitationStatus status, Instant asOf, InvitationPageCursor after, int limit) {
        List<InvitationPageRow> page = new ArrayList<>();
        for (var row : repository.findPage(owner, incoming, status == null ? null : status.name(), asOf, after, limit)) {
            page.add(new InvitationPageRow(mapper.toDomain(row.into(TUTORING_STUDENT_INVITATIONS)),
                    InvitationStatus.valueOf(row.get("status_as_of", String.class))));
        }
        return List.copyOf(page);
    }
    @Override
    public void insert(StudentInvitation invitation) {
        try {
            repository.insert(mapper.toPersistence(invitation));
        }
        catch (DuplicateKeyException exception) {
            if (PersistenceChecks.hasConstraint(exception, "uq_tutoring_student_invitations_pending")) {
                throw new InvitationCreationConflictException(InvitationCreationConflictException.Reason.ALREADY_PENDING);
            }
            throw exception;
        }
    }
    @Override
    public void update(StudentInvitation invitation) {
        repository.updateAll(List.of(mapper.toPersistence(invitation)));
    }
    @Override
    public void updateAll(List<StudentInvitation> invitations) {
        repository.updateAll(invitations.stream().map(mapper::toPersistence).toList());
    }
}
