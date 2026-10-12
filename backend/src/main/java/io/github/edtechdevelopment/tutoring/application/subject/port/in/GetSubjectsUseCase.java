package io.github.edtechdevelopment.tutoring.application.subject.port.in;

import io.github.edtechdevelopment.tutoring.application.subject.model.result.SubjectResult;

import java.util.List;

public interface GetSubjectsUseCase {

    List<SubjectResult> getSubjects();
}
