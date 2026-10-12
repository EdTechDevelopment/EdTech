package io.github.edtechdevelopment.tutoring.application.profile.mapper;

import io.github.edtechdevelopment.tutoring.api.model.profile.StudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.TeacherProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.search.StudentProfileCardView;
import io.github.edtechdevelopment.tutoring.api.model.profile.search.TeacherProfileCardView;
import io.github.edtechdevelopment.tutoring.api.model.profile.summary.StudentProfileSummary;
import io.github.edtechdevelopment.tutoring.api.model.profile.summary.TeacherProfileSummary;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.ProfileNameData;
import io.github.edtechdevelopment.tutoring.domain.profile.model.StudentProfile;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

public final class ProfileViewMapper {

    private static final int DESCRIPTION_PREVIEW_LENGTH = 200;

    public TeacherProfileView toView(TeacherProfile profile, int age) {
        return new TeacherProfileView(profile.userId(), age, profile.displayName(), profile.contactEmail().value(),
                profile.contactDetails(), subjectCodes(profile.subjectCodes()), profile.description(),
                profile.education(), profile.experienceYears(), profile.city(), photoUrl(profile.photoUrl()));
    }

    public StudentProfileView toView(StudentProfile profile, int age) {
        return new StudentProfileView(profile.userId(), age, profile.displayName(), profile.contactEmail().value(),
                profile.contactDetails(), subjectCodes(profile.subjectCodes()), photoUrl(profile.photoUrl()));
    }

    public TeacherProfileCardView toCard(TeacherProfile profile, int age) {
        return new TeacherProfileCardView(profile.userId(), profile.displayName(), age, photoUrl(profile.photoUrl()),
                subjectCodes(profile.subjectCodes()), profile.city(), descriptionPreview(profile.description()));
    }

    public StudentProfileCardView toCard(StudentProfile profile, int age) {
        return new StudentProfileCardView(profile.userId(), profile.displayName(), age, photoUrl(profile.photoUrl()),
                subjectCodes(profile.subjectCodes()));
    }

    public TeacherProfileSummary toTeacherSummary(ProfileNameData row) {
        return new TeacherProfileSummary(row.userId(), row.displayName());
    }

    public StudentProfileSummary toStudentSummary(ProfileNameData row) {
        return new StudentProfileSummary(row.userId(), row.displayName());
    }

    private static Set<String> subjectCodes(Set<SubjectCode> subjects) {
        Set<String> codes = new HashSet<>();
        for (SubjectCode subject : subjects) {
            codes.add(subject.value());
        }
        return Set.copyOf(codes);
    }

    private static String photoUrl(URI uri) {
        return uri == null ? null : uri.toString();
    }

    private static String descriptionPreview(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) <= DESCRIPTION_PREVIEW_LENGTH) {
            return normalized;
        }
        int previewEnd = normalized.offsetByCodePoints(0, DESCRIPTION_PREVIEW_LENGTH - 3);
        return normalized.substring(0, previewEnd).stripTrailing() + "...";
    }
}
