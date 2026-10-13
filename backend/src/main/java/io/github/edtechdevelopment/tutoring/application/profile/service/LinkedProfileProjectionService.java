package io.github.edtechdevelopment.tutoring.application.profile.service;

import io.github.edtechdevelopment.tutoring.api.model.profile.StudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.TeacherProfileView;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.profile.exception.ProfileReadConsistencyException;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileViewMapper;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.domain.profile.model.StudentProfile;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Transactional(readOnly = true)
public class LinkedProfileProjectionService {

    private final TeacherProfileRepository teacherRepository;
    private final StudentProfileRepository studentRepository;
    private final IdentityAccountGateway identityGateway;
    private final ProfileViewMapper mapper;
    private final Clock clock;

    public LinkedProfileProjectionService(
            TeacherProfileRepository teacherRepository, StudentProfileRepository studentRepository,
            IdentityAccountGateway identityGateway, ProfileViewMapper mapper, Clock clock) {

        this.teacherRepository = Objects.requireNonNull(teacherRepository, "Teacher repository must not be null");
        this.studentRepository = Objects.requireNonNull(studentRepository, "Student repository must not be null");
        this.identityGateway = Objects.requireNonNull(identityGateway, "Identity gateway must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Profile view mapper must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    public Map<UUID, TeacherProfileView> findTeachers(Set<UUID> teacherUserIds) {
        Set<UUID> ids = requireIds(teacherUserIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, TeacherProfile> profiles = teacherRepository.findByUserIds(ids);
        requireAllProfiles(ids, profiles.keySet());
        Set<UUID> activeIds = identityGateway.findActiveUserIds(ids);
        if (activeIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> ages = findAges(activeIds);
        Map<UUID, TeacherProfileView> views = new HashMap<>();
        for (UUID id : activeIds) {
            views.put(id, mapper.toView(profiles.get(id), requireAge(ages, id)));
        }
        return Map.copyOf(views);
    }

    public Map<UUID, StudentProfileView> findStudents(Set<UUID> studentUserIds) {
        Set<UUID> ids = requireIds(studentUserIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, StudentProfile> profiles = studentRepository.findByUserIds(ids);
        requireAllProfiles(ids, profiles.keySet());
        Set<UUID> activeIds = identityGateway.findActiveUserIds(ids);
        if (activeIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> ages = findAges(activeIds);
        Map<UUID, StudentProfileView> views = new HashMap<>();
        for (UUID id : activeIds) {
            views.put(id, mapper.toView(profiles.get(id), requireAge(ages, id)));
        }
        return Map.copyOf(views);
    }

    private Map<UUID, Integer> findAges(Set<UUID> ids) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return identityGateway.findAgesByIds(ids, today);
    }

    private static void requireAllProfiles(Set<UUID> requestedIds, Set<UUID> foundIds) {
        for (UUID id : requestedIds) {
            if (!foundIds.contains(id)) {
                throw new ProfileReadConsistencyException(id, "Linked profile is missing");
            }
        }
    }

    private static int requireAge(Map<UUID, Integer> ages, UUID userId) {
        Integer age = ages.get(userId);
        if (age == null || age < 0) {
            throw new ProfileReadConsistencyException(userId, "Profile account age is missing or invalid");
        }
        return age;
    }

    private static Set<UUID> requireIds(Set<UUID> ids) {
        if (ids == null || ids.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("User ids must not be null or contain null");
        }
        return Set.copyOf(ids);
    }
}
