package io.github.edtechdevelopment.tutoring.infrastructure.profile.persistence.teacher;

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

import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.TUTORING_TEACHER_PROFILES;
import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.TUTORING_TEACHER_PROFILE_SUBJECTS;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;

@Repository
public class TeacherProfileJooqRepository {
    private final DSLContext dsl;
    public TeacherProfileJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Map<UUID, TeacherProfilePersistenceData> findByUserIds(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        var selectedSubjects = multiset(select(TUTORING_TEACHER_PROFILE_SUBJECTS.SUBJECT_CODE).from(TUTORING_TEACHER_PROFILE_SUBJECTS)
                .where(TUTORING_TEACHER_PROFILE_SUBJECTS.USER_ID.eq(TUTORING_TEACHER_PROFILES.USER_ID))).as("selected_subjects");
        var rows = dsl.select(TUTORING_TEACHER_PROFILES.fields()).select(selectedSubjects)
                .from(TUTORING_TEACHER_PROFILES).where(TUTORING_TEACHER_PROFILES.USER_ID.in(ids)).fetch();
        Map<UUID, TeacherProfilePersistenceData> found = new HashMap<>();
        for (var row : rows) {
            var profile = row.into(TUTORING_TEACHER_PROFILES);
            Set<String> codes = Set.copyOf(row.get(selectedSubjects).getValues(TUTORING_TEACHER_PROFILE_SUBJECTS.SUBJECT_CODE));
            found.put(profile.getUserId(), new TeacherProfilePersistenceData(profile, codes));
        }
        return Map.copyOf(found);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TeacherProfilePersistenceData> findForUpdate(UUID id) {
        UUID found = dsl.select(TUTORING_TEACHER_PROFILES.USER_ID).from(TUTORING_TEACHER_PROFILES)
                .where(TUTORING_TEACHER_PROFILES.USER_ID.eq(id)).forUpdate().fetchOne(TUTORING_TEACHER_PROFILES.USER_ID);
        return found == null ? Optional.empty() : Optional.of(findByUserIds(Set.of(id)).get(id));
    }

    public boolean exists(UUID id) {
        return dsl.fetchExists(dsl.selectOne().from(TUTORING_TEACHER_PROFILES).where(TUTORING_TEACHER_PROFILES.USER_ID.eq(id)));
    }

    public Map<UUID, ProfileNameData> findNames(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProfileNameData> names = new HashMap<>();
        for (var row : dsl.select(TUTORING_TEACHER_PROFILES.USER_ID, TUTORING_TEACHER_PROFILES.DISPLAY_NAME).from(TUTORING_TEACHER_PROFILES)
                .where(TUTORING_TEACHER_PROFILES.USER_ID.in(ids)).fetch()) {
            UUID id = row.get(TUTORING_TEACHER_PROFILES.USER_ID);
            names.put(id, new ProfileNameData(id, row.get(TUTORING_TEACHER_PROFILES.DISPLAY_NAME)));
        }
        return Map.copyOf(names);
    }

    public List<PublicProfileSearchCandidate> findCandidates(SubjectCode subject, PublicProfilePageCursor after, int limit) {
        Condition condition = noCondition();
        if (subject != null) {
            condition = condition.and(org.jooq.impl.DSL.exists(selectOne().from(TUTORING_TEACHER_PROFILE_SUBJECTS)
                    .where(TUTORING_TEACHER_PROFILE_SUBJECTS.USER_ID.eq(TUTORING_TEACHER_PROFILES.USER_ID))
                    .and(TUTORING_TEACHER_PROFILE_SUBJECTS.SUBJECT_CODE.eq(subject.value()))));
        }
        if (after != null) {
            var time = after.createdAt().atOffset(ZoneOffset.UTC);
            condition = condition.and(TUTORING_TEACHER_PROFILES.CREATED_AT.lt(time).or(
                    TUTORING_TEACHER_PROFILES.CREATED_AT.eq(time).and(TUTORING_TEACHER_PROFILES.USER_ID.lt(after.userId()))));
        }
        List<PublicProfileSearchCandidate> found = new ArrayList<>();
        for (var row : dsl.select(TUTORING_TEACHER_PROFILES.USER_ID, TUTORING_TEACHER_PROFILES.CREATED_AT).from(TUTORING_TEACHER_PROFILES)
                .where(condition).orderBy(TUTORING_TEACHER_PROFILES.CREATED_AT.desc(), TUTORING_TEACHER_PROFILES.USER_ID.desc()).limit(limit).fetch()) {
            found.add(new PublicProfileSearchCandidate(row.get(TUTORING_TEACHER_PROFILES.USER_ID), row.get(TUTORING_TEACHER_PROFILES.CREATED_AT).toInstant()));
        }
        return List.copyOf(found);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(TeacherProfilePersistenceData data) {
        dsl.insertInto(TUTORING_TEACHER_PROFILES).set(data.profile()).execute();
        synchronizeSubjects(data.profile().getUserId(), data.subjectCodes());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(TeacherProfilePersistenceData data) {
        var row = data.profile();
        int changed = dsl.update(TUTORING_TEACHER_PROFILES)
                .set(TUTORING_TEACHER_PROFILES.DISPLAY_NAME, row.getDisplayName())
                .set(TUTORING_TEACHER_PROFILES.CONTACT_EMAIL, row.getContactEmail())
                .set(TUTORING_TEACHER_PROFILES.CONTACT_DETAILS, row.getContactDetails())
                .set(TUTORING_TEACHER_PROFILES.PHOTO_URL, row.getPhotoUrl())
                .set(TUTORING_TEACHER_PROFILES.DESCRIPTION, row.getDescription())
                .set(TUTORING_TEACHER_PROFILES.EDUCATION, row.getEducation())
                .set(TUTORING_TEACHER_PROFILES.EXPERIENCE_YEARS, row.getExperienceYears())
                .set(TUTORING_TEACHER_PROFILES.CITY, row.getCity())
                .where(TUTORING_TEACHER_PROFILES.USER_ID.eq(row.getUserId())).execute();
        PersistenceChecks.requireOneRow(changed);
        synchronizeSubjects(row.getUserId(), data.subjectCodes());
    }

    private void synchronizeSubjects(UUID id, Set<String> codes) {
        dsl.deleteFrom(TUTORING_TEACHER_PROFILE_SUBJECTS).where(TUTORING_TEACHER_PROFILE_SUBJECTS.USER_ID.eq(id))
                .and(TUTORING_TEACHER_PROFILE_SUBJECTS.SUBJECT_CODE.notIn(codes)).execute();
        for (String code : codes) {
            dsl.insertInto(TUTORING_TEACHER_PROFILE_SUBJECTS, TUTORING_TEACHER_PROFILE_SUBJECTS.USER_ID, TUTORING_TEACHER_PROFILE_SUBJECTS.SUBJECT_CODE)
                    .values(id, code).onConflictDoNothing().execute();
        }
    }
}
