package io.github.edtechdevelopment.tutoring.api.query;

import java.util.Set;

public interface TutoringSubjectQuery {

    boolean exists(String subjectCode);

    void requireAllExist(Set<String> subjectCodes);
}
