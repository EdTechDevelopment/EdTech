package io.github.edtechdevelopment.tutoring.application.subject.service;

import io.github.edtechdevelopment.tutoring.api.exception.UnknownSubjectsException;
import io.github.edtechdevelopment.tutoring.api.query.TutoringSubjectQuery;
import io.github.edtechdevelopment.tutoring.application.subject.port.out.SubjectRepository;
import io.github.edtechdevelopment.tutoring.domain.subject.exception.InvalidSubjectCodeException;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class TutoringSubjectQueryService implements TutoringSubjectQuery {

    private final SubjectRepository subjectRepository;

    public TutoringSubjectQueryService(SubjectRepository subjectRepository) {
        this.subjectRepository = Objects.requireNonNull(subjectRepository, "Subject repository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String subjectCode) {
        return subjectRepository.exists(toSubjectCode(subjectCode));
    }

    @Override
    @Transactional(readOnly = true)
    public void requireAllExist(Set<String> subjectCodes) {
        if (subjectCodes == null || subjectCodes.isEmpty()) {
            throw new IllegalArgumentException("Subject codes must not be null or empty");
        }

        Set<SubjectCode> requestedCodes = new HashSet<>();
        for (String subjectCode : subjectCodes) {
            requestedCodes.add(toSubjectCode(subjectCode));
        }

        Set<SubjectCode> existingCodes = subjectRepository.findExistingCodes(Set.copyOf(requestedCodes));
        Set<String> unknownCodes = new HashSet<>();
        for (SubjectCode requestedCode : requestedCodes) {
            if (!existingCodes.contains(requestedCode)) {
                unknownCodes.add(requestedCode.value());
            }
        }
        if (!unknownCodes.isEmpty()) {
            throw new UnknownSubjectsException(unknownCodes);
        }
    }

    private static SubjectCode toSubjectCode(String value) {
        try {
            return new SubjectCode(value);
        } catch (InvalidSubjectCodeException ignored) {
            throw new IllegalArgumentException("Subject code has invalid format");
        }
    }
}
