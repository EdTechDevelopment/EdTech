package io.github.edtechdevelopment.tutoring.infrastructure.profile.persistence.student;

import io.github.edtechdevelopment.tutoring.application.profile.model.query.ProfileNameData;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfilePageCursor;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfileSearchCandidate;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.PersistenceChecks;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.TUTORING_STUDENT_PROFILES;
import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.TUTORING_STUDENT_PROFILE_SUBJECTS;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;

@Repository
public class StudentProfileJooqRepository {
    private final DSLContext dsl;
    public StudentProfileJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Map<UUID, StudentProfilePersistenceData> findByUserIds(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        var selectedSubjects = multiset(select(TUTORING_STUDENT_PROFILE_SUBJECTS.SUBJECT_CODE).from(TUTORING_STUDENT_PROFILE_SUBJECTS)
                .where(TUTORING_STUDENT_PROFILE_SUBJECTS.USER_ID.eq(TUTORING_STUDENT_PROFILES.USER_ID))).as("selected_subjects");
        var rows = dsl.select(TUTORING_STUDENT_PROFILES.fields()).select(selectedSubjects)
                .from(TUTORING_STUDENT_PROFILES).where(TUTORING_STUDENT_PROFILES.USER_ID.in(ids)).fetch();
        Map<UUID, StudentProfilePersistenceData> found = new HashMap<>();
        for (var row : rows) {
            var profile = row.into(TUTORING_STUDENT_PROFILES);
            Set<String> codes = Set.copyOf(row.get(selectedSubjects).getValues(TUTORING_STUDENT_PROFILE_SUBJECTS.SUBJECT_CODE));
            found.put(profile.getUserId(), new StudentProfilePersistenceData(profile, codes));
        }
        return Map.copyOf(found);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<StudentProfilePersistenceData> findForUpdate(UUID id) {
        UUID found = dsl.select(TUTORING_STUDENT_PROFILES.USER_ID).from(TUTORING_STUDENT_PROFILES)
                .where(TUTORING_STUDENT_PROFILES.USER_ID.eq(id)).forUpdate().fetchOne(TUTORING_STUDENT_PROFILES.USER_ID);
        return found == null ? Optional.empty() : Optional.of(findByUserIds(Set.of(id)).get(id));
    }

    public boolean exists(UUID id) {
        return dsl.fetchExists(dsl.selectOne().from(TUTORING_STUDENT_PROFILES).where(TUTORING_STUDENT_PROFILES.USER_ID.eq(id)));
    }

    public Map<UUID, ProfileNameData> findNames(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProfileNameData> names = new HashMap<>();
        for (var row : dsl.select(TUTORING_STUDENT_PROFILES.USER_ID, TUTORING_STUDENT_PROFILES.DISPLAY_NAME).from(TUTORING_STUDENT_PROFILES)
                .where(TUTORING_STUDENT_PROFILES.USER_ID.in(ids)).fetch()) {
            UUID id = row.get(TUTORING_STUDENT_PROFILES.USER_ID);
            names.put(id, new ProfileNameData(id, row.get(TUTORING_STUDENT_PROFILES.DISPLAY_NAME)));
        }
        return Map.copyOf(names);
    }

    public List<PublicProfileSearchCandidate> findCandidates(SubjectCode subject, PublicProfilePageCursor after, int limit) {
        Condition condition = noCondition();
        if (subject != null) {
            condition = condition.and(org.jooq.impl.DSL.exists(selectOne().from(TUTORING_STUDENT_PROFILE_SUBJECTS)
                    .where(TUTORING_STUDENT_PROFILE_SUBJECTS.USER_ID.eq(TUTORING_STUDENT_PROFILES.USER_ID))
                    .and(TUTORING_STUDENT_PROFILE_SUBJECTS.SUBJECT_CODE.eq(subject.value()))));
        }
        if (after != null) {
            var time = after.createdAt().atOffset(ZoneOffset.UTC);
            condition = condition.and(TUTORING_STUDENT_PROFILES.CREATED_AT.lt(time).or(
                    TUTORING_STUDENT_PROFILES.CREATED_AT.eq(time).and(TUTORING_STUDENT_PROFILES.USER_ID.lt(after.userId()))));
        }
        List<PublicProfileSearchCandidate> found = new ArrayList<>();
        for (var row : dsl.select(TUTORING_STUDENT_PROFILES.USER_ID, TUTORING_STUDENT_PROFILES.CREATED_AT).from(TUTORING_STUDENT_PROFILES)
                .where(condition).orderBy(TUTORING_STUDENT_PROFILES.CREATED_AT.desc(), TUTORING_STUDENT_PROFILES.USER_ID.desc()).limit(limit).fetch()) {
            found.add(new PublicProfileSearchCandidate(row.get(TUTORING_STUDENT_PROFILES.USER_ID), row.get(TUTORING_STUDENT_PROFILES.CREATED_AT).toInstant()));
        }
        return List.copyOf(found);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(StudentProfilePersistenceData data) {
        dsl.insertInto(TUTORING_STUDENT_PROFILES).set(data.profile()).execute();
        synchronizeSubjects(data.profile().getUserId(), data.subjectCodes());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(StudentProfilePersistenceData data) {
        var row = data.profile();
        int changed = dsl.update(TUTORING_STUDENT_PROFILES)
                .set(TUTORING_STUDENT_PROFILES.DISPLAY_NAME, row.getDisplayName())
                .set(TUTORING_STUDENT_PROFILES.CONTACT_EMAIL, row.getContactEmail())
                .set(TUTORING_STUDENT_PROFILES.CONTACT_DETAILS, row.getContactDetails())
                .set(TUTORING_STUDENT_PROFILES.PHOTO_URL, row.getPhotoUrl())
                
                .where(TUTORING_STUDENT_PROFILES.USER_ID.eq(row.getUserId())).execute();
        PersistenceChecks.requireOneRow(changed);
        synchronizeSubjects(row.getUserId(), data.subjectCodes());
    }

    private void synchronizeSubjects(UUID id, Set<String> codes) {
        dsl.deleteFrom(TUTORING_STUDENT_PROFILE_SUBJECTS).where(TUTORING_STUDENT_PROFILE_SUBJECTS.USER_ID.eq(id))
                .and(TUTORING_STUDENT_PROFILE_SUBJECTS.SUBJECT_CODE.notIn(codes)).execute();
        for (String code : codes) {
            dsl.insertInto(TUTORING_STUDENT_PROFILE_SUBJECTS, TUTORING_STUDENT_PROFILE_SUBJECTS.USER_ID, TUTORING_STUDENT_PROFILE_SUBJECTS.SUBJECT_CODE)
                    .values(id, code).onConflictDoNothing().execute();
        }
    }
}
