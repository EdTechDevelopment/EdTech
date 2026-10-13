package io.github.edtechdevelopment.tutoring.application.subject.port.out;

import io.github.edtechdevelopment.tutoring.domain.subject.model.Subject;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;

import java.util.List;
import java.util.Set;

public interface SubjectRepository {

    List<Subject> findAll();

    boolean exists(SubjectCode subjectCode);

    Set<SubjectCode> findExistingCodes(Set<SubjectCode> subjectCodes);
}
