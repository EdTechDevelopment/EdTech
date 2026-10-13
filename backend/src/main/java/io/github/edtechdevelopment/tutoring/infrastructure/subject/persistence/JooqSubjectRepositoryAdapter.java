package io.github.edtechdevelopment.tutoring.infrastructure.subject.persistence;

import io.github.edtechdevelopment.tutoring.application.subject.port.out.SubjectRepository;
import io.github.edtechdevelopment.tutoring.domain.subject.model.Subject;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.TUTORING_SUBJECTS;

@Repository
public class JooqSubjectRepositoryAdapter implements SubjectRepository {
    private final DSLContext dsl;
    public JooqSubjectRepositoryAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<Subject> findAll() {
        List<Subject> subjects = new ArrayList<>();
        for (var row : dsl.selectFrom(TUTORING_SUBJECTS).fetch()) {
            subjects.add(new Subject(new SubjectCode(row.getCode()), row.getName()));
        }
        return List.copyOf(subjects);
    }

    @Override
    public boolean exists(SubjectCode code) {
        return dsl.fetchExists(dsl.selectOne().from(TUTORING_SUBJECTS).where(TUTORING_SUBJECTS.CODE.eq(code.value())));
    }

    @Override
    public Set<SubjectCode> findExistingCodes(Set<SubjectCode> codes) {
        if (codes.isEmpty()) {
            return Set.of();
        }
        Set<String> values = new HashSet<>();
        for (SubjectCode code : codes) {
            values.add(code.value());
        }
        Set<SubjectCode> found = new HashSet<>();
        for (String code : dsl.select(TUTORING_SUBJECTS.CODE).from(TUTORING_SUBJECTS)
                .where(TUTORING_SUBJECTS.CODE.in(values)).fetch(TUTORING_SUBJECTS.CODE)) {
            found.add(new SubjectCode(code));
        }
        return Set.copyOf(found);
    }
}
