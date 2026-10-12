package io.github.edtechdevelopment.tutoring.application.relationship.service;

import io.github.edtechdevelopment.tutoring.api.exception.ProfileNotFoundException;
import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;
import io.github.edtechdevelopment.tutoring.api.model.profile.StudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.TeacherProfileView;
import io.github.edtechdevelopment.tutoring.api.query.TutoringProfileQuery;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.service.LinkedProfileProjectionService;
import io.github.edtechdevelopment.tutoring.application.relationship.mapper.RelationshipResultMapper;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.ListStudentTeachersQuery;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.ListTeacherStudentsQuery;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.RelationshipDirection;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.RelationshipPageCursor;
import io.github.edtechdevelopment.tutoring.application.relationship.model.result.StudentRelationshipPageResult;
import io.github.edtechdevelopment.tutoring.application.relationship.model.result.StudentRelationshipResult;
import io.github.edtechdevelopment.tutoring.application.relationship.model.result.TeacherRelationshipPageResult;
import io.github.edtechdevelopment.tutoring.application.relationship.model.result.TeacherRelationshipResult;
import io.github.edtechdevelopment.tutoring.application.relationship.port.in.ListStudentTeachersUseCase;
import io.github.edtechdevelopment.tutoring.application.relationship.port.in.ListTeacherStudentsUseCase;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.RelationshipCursorCodec;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import io.github.edtechdevelopment.tutoring.domain.relationship.model.TeacherStudent;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class RelationshipListQueryService implements ListTeacherStudentsUseCase, ListStudentTeachersUseCase {

    private final StudentProfileRepository studentProfiles;
    private final TeacherStudentRepository relationships;
    private final LinkedProfileProjectionService linkedProfiles;
    private final RelationshipCursorCodec cursorCodec;
    private final RelationshipResultMapper mapper;
    private final TutoringProfileQuery profileQuery;

    public RelationshipListQueryService(
            StudentProfileRepository studentProfiles,
            TeacherStudentRepository relationships,
            LinkedProfileProjectionService linkedProfiles,
            RelationshipCursorCodec cursorCodec,
            RelationshipResultMapper mapper,
            TutoringProfileQuery profileQuery) {

        this.studentProfiles = Objects.requireNonNull(studentProfiles, "Student profile repository must not be null");
        this.relationships = Objects.requireNonNull(relationships, "Relationship repository must not be null");
        this.linkedProfiles = Objects.requireNonNull(linkedProfiles, "Linked profile service must not be null");
        this.cursorCodec = Objects.requireNonNull(cursorCodec, "Relationship cursor codec must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Relationship result mapper must not be null");
        this.profileQuery = Objects.requireNonNull(profileQuery, "Tutoring profile query must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public StudentRelationshipPageResult execute(ListTeacherStudentsQuery query) {
        Objects.requireNonNull(query, "List teacher students query must not be null");
        profileQuery.requireTeacherProfile(query.teacherUserId());

        RelationshipDirection direction = RelationshipDirection.TEACHER_STUDENTS;
        RelationshipPageCursor after = decodeCursor(query.cursor(), query.teacherUserId(), direction);
        List<StudentRelationshipResult> matches = new ArrayList<>(query.limit() + 1);
        RelationshipPageCursor lastReturned = null;
        RelationshipPageCursor scanAfter = after;
        int fetchLimit = query.limit() + 1;

        while (matches.size() <= query.limit()) {
            List<TeacherStudent> batch = relationships.findByTeacher(query.teacherUserId(), scanAfter, fetchLimit);
            if (batch.isEmpty()) {
                break;
            }

            Set<UUID> studentIds = new HashSet<>();
            for (TeacherStudent relationship : batch) {
                studentIds.add(relationship.studentUserId());
            }
            Map<UUID, StudentProfileView> profiles = linkedProfiles.findStudents(Set.copyOf(studentIds));

            for (TeacherStudent relationship : batch) {
                RelationshipPageCursor position = new RelationshipPageCursor(
                        relationship.createdAt(), relationship.studentUserId());
                scanAfter = position;
                StudentProfileView profile = profiles.get(relationship.studentUserId());
                if (profile == null) {
                    continue;
                }
                matches.add(mapper.toStudentResult(relationship.createdAt(), profile));
                if (matches.size() <= query.limit()) {
                    lastReturned = position;
                } else {
                    break;
                }
            }
            if (matches.size() > query.limit() || batch.size() < fetchLimit) {
                break;
            }
        }

        String nextCursor = null;
        if (matches.size() > query.limit()) {
            matches.removeLast();
            nextCursor = cursorCodec.encode(lastReturned, query.teacherUserId(), direction);
        }
        return new StudentRelationshipPageResult(matches, nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherRelationshipPageResult execute(ListStudentTeachersQuery query) {
        Objects.requireNonNull(query, "List student teachers query must not be null");
        if (!studentProfiles.existsByUserId(query.studentUserId())) {
            throw new ProfileNotFoundException(ProfileTypeView.STUDENT, query.studentUserId());
        }

        RelationshipDirection direction = RelationshipDirection.STUDENT_TEACHERS;
        RelationshipPageCursor after = decodeCursor(query.cursor(), query.studentUserId(), direction);
        List<TeacherRelationshipResult> matches = new ArrayList<>(query.limit() + 1);
        RelationshipPageCursor lastReturned = null;
        RelationshipPageCursor scanAfter = after;
        int fetchLimit = query.limit() + 1;

        while (matches.size() <= query.limit()) {
            List<TeacherStudent> batch = relationships.findByStudent(query.studentUserId(), scanAfter, fetchLimit);
            if (batch.isEmpty()) {
                break;
            }

            Set<UUID> teacherIds = new HashSet<>();
            for (TeacherStudent relationship : batch) {
                teacherIds.add(relationship.teacherUserId());
            }
            Map<UUID, TeacherProfileView> profiles = linkedProfiles.findTeachers(Set.copyOf(teacherIds));

            for (TeacherStudent relationship : batch) {
                RelationshipPageCursor position = new RelationshipPageCursor(
                        relationship.createdAt(), relationship.teacherUserId());
                scanAfter = position;
                TeacherProfileView profile = profiles.get(relationship.teacherUserId());
                if (profile == null) {
                    continue;
                }
                matches.add(mapper.toTeacherResult(relationship.createdAt(), profile));
                if (matches.size() <= query.limit()) {
                    lastReturned = position;
                } else {
                    break;
                }
            }
            if (matches.size() > query.limit() || batch.size() < fetchLimit) {
                break;
            }
        }

        String nextCursor = null;
        if (matches.size() > query.limit()) {
            matches.removeLast();
            nextCursor = cursorCodec.encode(lastReturned, query.studentUserId(), direction);
        }
        return new TeacherRelationshipPageResult(matches, nextCursor);
    }

    private RelationshipPageCursor decodeCursor(
            String cursor, UUID actorUserId, RelationshipDirection direction) {

        return cursor == null ? null : cursorCodec.decode(cursor, actorUserId, direction);
    }
}
