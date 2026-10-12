package io.github.edtechdevelopment.tutoring.application.profile.service;

import io.github.edtechdevelopment.tutoring.api.command.profile.CreateInitialProfilesCommand;
import io.github.edtechdevelopment.tutoring.api.command.profile.CreateStudentProfileCommand;
import io.github.edtechdevelopment.tutoring.api.command.profile.TutoringProfileCreationCommands;
import io.github.edtechdevelopment.tutoring.api.exception.UnknownSubjectsException;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.StudentProfileData;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;
import io.github.edtechdevelopment.tutoring.api.query.TutoringSubjectQuery;
import io.github.edtechdevelopment.tutoring.api.query.TutoringProfileQuery;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileInputMapper;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileViewMapper;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import io.github.edtechdevelopment.tutoring.application.profile.model.command.UpdateStudentProfileCommand;
import io.github.edtechdevelopment.tutoring.application.profile.port.in.UpdateStudentProfileUseCase;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileTransactionTest {

    private final UUID userId = UUID.randomUUID();
    private final Connection connection = mock(Connection.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
    private final TeacherProfileRepository teacherRepository = mock(TeacherProfileRepository.class);
    private final StudentProfileRepository studentRepository = mock(StudentProfileRepository.class);
    private final IdentityAccountGateway identityGateway = mock(IdentityAccountGateway.class);
    private final TutoringSubjectQuery subjectQuery = mock(TutoringSubjectQuery.class);
    private final ProfileInputMapper mapper = new ProfileInputMapper();
    private TutoringProfileCreationCommands registration;

    @BeforeEach
    void setUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        registration = (TutoringProfileCreationCommands) transactionalProxy(new RegistrationProfileService(
                teacherRepository, studentRepository, identityGateway, subjectQuery, mapper, ProfileReadFixtures.CLOCK));
    }

    @Test
    void creationRequiresWorkflowTransactionBeforeCallingDependencies() {
        assertThrows(IllegalTransactionStateException.class, () -> registration.createStudentProfile(
                new CreateStudentProfileCommand(userId, studentData())));

        verifyNoInteractions(identityGateway, subjectQuery, teacherRepository, studentRepository);
    }

    @Test
    void creationJoinsWorkflowTransactionAndDoesNotCommitItSeparately() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            registration.createStudentProfile(new CreateStudentProfileCommand(userId, studentData()));
            try {
                verify(connection, never()).commit();
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });

        verify(studentRepository).insert(any());
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void secondInsertFailureRollsBackWholeWorkflowTransaction() throws Exception {
        doThrow(new IllegalStateException("Storage failed")).when(studentRepository).insert(any());
        TeacherProfileData teacher = new TeacherProfileData("Teacher", "teacher@example.com", List.of(),
                Set.of("MATH"), null, null, null, null, null);

        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> registration.createInitialProfiles(new CreateInitialProfilesCommand(
                        userId, Optional.of(teacher), Optional.of(studentData())))));

        verify(teacherRepository).insert(any());
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void updateStartsLocalTransactionAndRollsItBackOnFailure() throws Exception {
        UpdateStudentProfileUseCase update = (UpdateStudentProfileUseCase) transactionalProxy(
                new StudentProfileUpdateService(studentRepository, subjectQuery, mapper));
        doThrow(new UnknownSubjectsException(Set.of("MATH")))
                .when(subjectQuery).requireAllExist(Set.of("MATH"));

        assertThrows(UnknownSubjectsException.class, () -> update.execute(new UpdateStudentProfileCommand(
                userId, "Student", "student@example.com", List.of(), Set.of("MATH"), null)));

        verify(connection).rollback();
        verify(connection, never()).commit();
        verifyNoInteractions(studentRepository);
    }

    private Object transactionalProxy(Object service) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return factory.getProxy();
    }

    @Test
    void profileReadsUseReadOnlyTransaction() throws Exception {
        TutoringProfileQuery query = (TutoringProfileQuery) transactionalProxy(new ProfileQueryService(
                teacherRepository, studentRepository, mock(TeacherStudentRepository.class), identityGateway,
                new ProfileViewMapper(), ProfileReadFixtures.CLOCK));
        when(teacherRepository.findByUserId(userId)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return Optional.empty();
        });

        query.findTeacherProfile(userId);

        verify(connection).setReadOnly(true);
        verify(connection).commit();
    }

    private static StudentProfileData studentData() {
        return new StudentProfileData("Student", "student@example.com", List.of(), Set.of(), null);
    }
}
