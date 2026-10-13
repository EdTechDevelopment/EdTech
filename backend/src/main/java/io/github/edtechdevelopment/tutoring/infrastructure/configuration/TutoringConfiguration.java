package io.github.edtechdevelopment.tutoring.infrastructure.configuration;

import io.github.edtechdevelopment.tutoring.api.query.TutoringProfileQuery;
import io.github.edtechdevelopment.tutoring.api.query.TutoringSubjectQuery;
import io.github.edtechdevelopment.tutoring.application.email.port.out.NormalizedEmailLock;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.in.CommandIdempotency;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.out.CommandOperationRepository;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.out.ProcessedIdentityEventRepository;
import io.github.edtechdevelopment.tutoring.application.idempotency.service.CommandIdempotencyService;
import io.github.edtechdevelopment.tutoring.application.idempotency.service.IdentityEventDeduplicationService;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.integration.identity.service.HandleAccountEmailVerifiedService;
import io.github.edtechdevelopment.tutoring.application.invitation.mapper.InvitationResultMapper;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.InvitationCursorCodec;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.InvitationNotificationSender;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.StudentInvitationRepository;
import io.github.edtechdevelopment.tutoring.application.invitation.service.CreateStudentInvitationService;
import io.github.edtechdevelopment.tutoring.application.invitation.service.InvitationQueryService;
import io.github.edtechdevelopment.tutoring.application.invitation.service.StudentInvitationResponseService;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileInputMapper;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileViewMapper;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.PublicProfileCursorCodec;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.service.LinkedProfileProjectionService;
import io.github.edtechdevelopment.tutoring.application.profile.service.ProfileQueryService;
import io.github.edtechdevelopment.tutoring.application.profile.service.PublicProfileSearchService;
import io.github.edtechdevelopment.tutoring.application.profile.service.RegistrationProfileService;
import io.github.edtechdevelopment.tutoring.application.profile.service.StudentProfileUpdateService;
import io.github.edtechdevelopment.tutoring.application.profile.service.TeacherProfileUpdateService;
import io.github.edtechdevelopment.tutoring.application.relationship.mapper.RelationshipResultMapper;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.RelationshipCursorCodec;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import io.github.edtechdevelopment.tutoring.application.relationship.service.RelationshipListQueryService;
import io.github.edtechdevelopment.tutoring.application.relationship.service.TutoringRelationshipCommandService;
import io.github.edtechdevelopment.tutoring.application.relationship.service.TutoringRelationshipQueryService;
import io.github.edtechdevelopment.tutoring.application.subject.mapper.SubjectResultMapper;
import io.github.edtechdevelopment.tutoring.application.subject.port.out.SubjectRepository;
import io.github.edtechdevelopment.tutoring.application.subject.service.GetSubjectsService;
import io.github.edtechdevelopment.tutoring.application.subject.service.TutoringSubjectQueryService;
import io.github.edtechdevelopment.tutoring.infrastructure.cursor.TutoringCursorProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TutoringCursorProperties.class)
public class TutoringConfiguration {

    @Bean
    SubjectResultMapper subjectResultMapper() {
        return new SubjectResultMapper();
    }

    @Bean
    ProfileInputMapper profileInputMapper() {
        return new ProfileInputMapper();
    }

    @Bean
    ProfileViewMapper profileViewMapper() {
        return new ProfileViewMapper();
    }

    @Bean
    InvitationResultMapper invitationResultMapper() {
        return new InvitationResultMapper();
    }

    @Bean
    RelationshipResultMapper relationshipResultMapper() {
        return new RelationshipResultMapper();
    }

    @Bean
    TutoringSubjectQueryService tutoringSubjectQueryService(
            SubjectRepository subjectRepository) {
        return new TutoringSubjectQueryService(subjectRepository);
    }

    @Bean
    GetSubjectsService getSubjectsService(
            SubjectRepository subjectRepository, SubjectResultMapper subjectResultMapper) {
        return new GetSubjectsService(subjectRepository, subjectResultMapper);
    }

    @Bean
    HandleAccountEmailVerifiedService handleAccountEmailVerifiedService(
            IdentityEventDeduplicationService eventDeduplication, IdentityAccountGateway identity, NormalizedEmailLock emailLock, StudentInvitationRepository invitations, Clock clock) {
        return new HandleAccountEmailVerifiedService(eventDeduplication, identity, emailLock, invitations, clock);
    }

    @Bean
    IdentityEventDeduplicationService identityEventDeduplicationService(
            ProcessedIdentityEventRepository repository) {
        return new IdentityEventDeduplicationService(repository);
    }

    @Bean
    CommandIdempotencyService commandIdempotencyService(
            CommandOperationRepository repository) {
        return new CommandIdempotencyService(repository);
    }

    @Bean
    TeacherProfileUpdateService teacherProfileUpdateService(
            TeacherProfileRepository teacherProfileRepository, TutoringSubjectQuery subjectQuery, ProfileInputMapper profileInputMapper) {
        return new TeacherProfileUpdateService(teacherProfileRepository, subjectQuery, profileInputMapper);
    }

    @Bean
    StudentProfileUpdateService studentProfileUpdateService(
            StudentProfileRepository studentProfileRepository, TutoringSubjectQuery subjectQuery, ProfileInputMapper profileInputMapper) {
        return new StudentProfileUpdateService(studentProfileRepository, subjectQuery, profileInputMapper);
    }

    @Bean
    RegistrationProfileService registrationProfileService(
            TeacherProfileRepository teacherProfileRepository, StudentProfileRepository studentProfileRepository, IdentityAccountGateway identityAccountGateway, TutoringSubjectQuery subjectQuery, ProfileInputMapper profileInputMapper, Clock clock) {
        return new RegistrationProfileService(teacherProfileRepository, studentProfileRepository, identityAccountGateway, subjectQuery, profileInputMapper, clock);
    }

    @Bean
    PublicProfileSearchService publicProfileSearchService(
            TeacherProfileRepository teacherRepository, StudentProfileRepository studentRepository, IdentityAccountGateway identityGateway, TutoringSubjectQuery subjectQuery, PublicProfileCursorCodec cursorCodec, ProfileViewMapper mapper, Clock clock) {
        return new PublicProfileSearchService(teacherRepository, studentRepository, identityGateway, subjectQuery, cursorCodec, mapper, clock);
    }

    @Bean
    ProfileQueryService profileQueryService(
            TeacherProfileRepository teacherRepository, StudentProfileRepository studentRepository, TeacherStudentRepository relationshipRepository, IdentityAccountGateway identityGateway, ProfileViewMapper mapper, Clock clock) {
        return new ProfileQueryService(teacherRepository, studentRepository, relationshipRepository, identityGateway, mapper, clock);
    }

    @Bean
    LinkedProfileProjectionService linkedProfileProjectionService(
            TeacherProfileRepository teacherRepository, StudentProfileRepository studentRepository, IdentityAccountGateway identityGateway, ProfileViewMapper mapper, Clock clock) {
        return new LinkedProfileProjectionService(teacherRepository, studentRepository, identityGateway, mapper, clock);
    }

    @Bean
    StudentInvitationResponseService studentInvitationResponseService(
            StudentInvitationRepository invitations, StudentProfileRepository studentProfiles, TeacherStudentRepository relationships, CommandIdempotency idempotency, Clock clock) {
        return new StudentInvitationResponseService(invitations, studentProfiles, relationships, idempotency, clock);
    }

    @Bean
    InvitationQueryService invitationQueryService(
            TeacherProfileRepository teacherProfiles, StudentInvitationRepository invitations, InvitationCursorCodec cursorCodec, TutoringProfileQuery profiles, InvitationResultMapper mapper, Clock clock) {
        return new InvitationQueryService(teacherProfiles, invitations, cursorCodec, profiles, mapper, clock);
    }

    @Bean
    CreateStudentInvitationService createStudentInvitationService(
            TeacherProfileRepository teacherProfiles, TeacherStudentRepository relationships, StudentInvitationRepository invitations, IdentityAccountGateway identity, NormalizedEmailLock emailLock, InvitationNotificationSender notifications, CommandIdempotency idempotency, InvitationResultMapper mapper, Clock clock) {
        return new CreateStudentInvitationService(teacherProfiles, relationships, invitations, identity, emailLock, notifications, idempotency, mapper, clock);
    }

    @Bean
    TutoringRelationshipQueryService tutoringRelationshipQueryService(
            TeacherStudentRepository relationships) {
        return new TutoringRelationshipQueryService(relationships);
    }

    @Bean
    TutoringRelationshipCommandService tutoringRelationshipCommandService(
            TeacherStudentRepository relationships, CommandIdempotency idempotency, Clock clock) {
        return new TutoringRelationshipCommandService(relationships, idempotency, clock);
    }

    @Bean
    RelationshipListQueryService relationshipListQueryService(
            StudentProfileRepository studentProfiles, TeacherStudentRepository relationships, LinkedProfileProjectionService linkedProfiles, RelationshipCursorCodec cursorCodec, RelationshipResultMapper mapper, TutoringProfileQuery profileQuery) {
        return new RelationshipListQueryService(studentProfiles, relationships, linkedProfiles, cursorCodec, mapper, profileQuery);
    }
}
