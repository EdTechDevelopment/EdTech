package io.github.edtechdevelopment.tutoring.infrastructure.relationship.persistence;

import io.github.edtechdevelopment.tutoring.application.relationship.exception.RelationshipAlreadyExistsException;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.RelationshipPageCursor;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import io.github.edtechdevelopment.tutoring.domain.relationship.model.TeacherStudent;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.PersistenceChecks;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.tables.records.TutoringTeacherStudentsRecord;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.TUTORING_TEACHER_STUDENTS;

@Repository
public class JooqTeacherStudentRepositoryAdapter implements TeacherStudentRepository {
    private final DSLContext dsl;
    public JooqTeacherStudentRepositoryAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    private Condition pair(UUID teacher, UUID student) {
        return TUTORING_TEACHER_STUDENTS.TEACHER_USER_ID.eq(teacher).and(TUTORING_TEACHER_STUDENTS.STUDENT_USER_ID.eq(student));
    }
    @Override
    public boolean exists(UUID teacher, UUID student) {
        return dsl.fetchExists(dsl.selectOne().from(TUTORING_TEACHER_STUDENTS).where(pair(teacher, student)));
    }
    @Override
    public Set<UUID> findLinkedStudentUserIds(UUID teacher, Set<UUID> students) {
        if (students.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(dsl.select(TUTORING_TEACHER_STUDENTS.STUDENT_USER_ID).from(TUTORING_TEACHER_STUDENTS)
                .where(TUTORING_TEACHER_STUDENTS.TEACHER_USER_ID.eq(teacher))
                .and(TUTORING_TEACHER_STUDENTS.STUDENT_USER_ID.in(students)).fetchSet(TUTORING_TEACHER_STUDENTS.STUDENT_USER_ID));
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TeacherStudent> findForUpdate(UUID teacher, UUID student) {
        return dsl.selectFrom(TUTORING_TEACHER_STUDENTS).where(pair(teacher, student)).forUpdate().fetchOptional().map(this::toDomain);
    }
    @Override
    public List<TeacherStudent> findByTeacher(UUID teacher, RelationshipPageCursor after, int limit) {
        return findPage(TUTORING_TEACHER_STUDENTS.TEACHER_USER_ID, TUTORING_TEACHER_STUDENTS.STUDENT_USER_ID, teacher, after, limit);
    }
    @Override
    public List<TeacherStudent> findByStudent(UUID student, RelationshipPageCursor after, int limit) {
        return findPage(TUTORING_TEACHER_STUDENTS.STUDENT_USER_ID, TUTORING_TEACHER_STUDENTS.TEACHER_USER_ID, student, after, limit);
    }
    private List<TeacherStudent> findPage(Field<UUID> owner, Field<UUID> other, UUID id, RelationshipPageCursor after, int limit) {
        Condition condition = owner.eq(id);
        if (after != null) {
            var time = after.createdAt().atOffset(ZoneOffset.UTC);
            condition = condition.and(TUTORING_TEACHER_STUDENTS.CREATED_AT.lt(time).or(
                    TUTORING_TEACHER_STUDENTS.CREATED_AT.eq(time).and(other.lt(after.otherUserId()))));
        }
        return dsl.selectFrom(TUTORING_TEACHER_STUDENTS).where(condition)
                .orderBy(TUTORING_TEACHER_STUDENTS.CREATED_AT.desc(), other.desc()).limit(limit).fetch().map(this::toDomain);
    }
    private TeacherStudent toDomain(TutoringTeacherStudentsRecord row) {
        return TeacherStudent.reconstitute(row.getTeacherUserId(), row.getStudentUserId(), row.getCreatedAt().toInstant());
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(TeacherStudent relationship) {
        int inserted = dsl.insertInto(TUTORING_TEACHER_STUDENTS)
                .set(TUTORING_TEACHER_STUDENTS.TEACHER_USER_ID, relationship.teacherUserId())
                .set(TUTORING_TEACHER_STUDENTS.STUDENT_USER_ID, relationship.studentUserId())
                .set(TUTORING_TEACHER_STUDENTS.CREATED_AT, relationship.createdAt().atOffset(ZoneOffset.UTC))
                .onConflict(TUTORING_TEACHER_STUDENTS.TEACHER_USER_ID, TUTORING_TEACHER_STUDENTS.STUDENT_USER_ID)
                .doNothing().execute();
        if (inserted == 0) {
            throw new RelationshipAlreadyExistsException();
        }
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(TeacherStudent relationship) {
        PersistenceChecks.requireOneRow(dsl.deleteFrom(TUTORING_TEACHER_STUDENTS)
                .where(pair(relationship.teacherUserId(), relationship.studentUserId())).execute());
    }
}
