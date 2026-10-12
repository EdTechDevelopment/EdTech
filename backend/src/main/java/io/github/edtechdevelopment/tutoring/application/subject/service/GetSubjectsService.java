package io.github.edtechdevelopment.tutoring.application.subject.service;

import io.github.edtechdevelopment.tutoring.application.subject.exception.SubjectNotFoundException;
import io.github.edtechdevelopment.tutoring.application.subject.mapper.SubjectResultMapper;
import io.github.edtechdevelopment.tutoring.application.subject.model.result.SubjectResult;
import io.github.edtechdevelopment.tutoring.application.subject.port.in.GetSubjectsUseCase;
import io.github.edtechdevelopment.tutoring.application.subject.port.out.SubjectRepository;
import io.github.edtechdevelopment.tutoring.domain.subject.model.Subject;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class GetSubjectsService implements GetSubjectsUseCase {

    private final SubjectRepository subjectRepository;
    private final SubjectResultMapper subjectResultMapper;

    public GetSubjectsService(SubjectRepository subjectRepository, SubjectResultMapper subjectResultMapper) {
        this.subjectRepository = Objects.requireNonNull(subjectRepository, "Subject repository must not be null");
        this.subjectResultMapper = Objects.requireNonNull(subjectResultMapper, "Subject result mapper must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResult> getSubjects() {
        List<Subject> subjects = subjectRepository.findAll();
        if (subjects.isEmpty()) {
            throw new SubjectNotFoundException();
        }

        List<SubjectResult> results = new ArrayList<>(subjects.size());
        for (Subject subject : subjects) {
            results.add(subjectResultMapper.toResult(subject));
        }
        results.sort(Comparator.comparing(SubjectResult::name));
        return List.copyOf(results);
    }
}
