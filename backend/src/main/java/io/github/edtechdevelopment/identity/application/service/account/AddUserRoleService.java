package io.github.edtechdevelopment.identity.application.service.account;

import io.github.edtechdevelopment.identity.application.command.account.AssignRoleCommand;
import io.github.edtechdevelopment.identity.application.exception.InvalidUseCaseInputException;
import io.github.edtechdevelopment.identity.application.port.in.account.AddUserRoleUseCase;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class AddUserRoleService implements AddUserRoleUseCase {

    private final UserRepository userRepository;
    private final TimeProvider timeProvider;

    public AddUserRoleService(
            UserRepository userRepository,
            TimeProvider timeProvider
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "User repository must not be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "Time provider must not be null");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void addRole(AssignRoleCommand command) {
        if (command == null || command.userId() == null || command.role() == null) {
            throw new InvalidUseCaseInputException("User id and role must be provided");
        }

        UserRole role = UserRole.valueOf(command.role().name());
        User user = userRepository.findByIdForUpdate(command.userId())
                .orElseThrow(() -> new InvalidUseCaseInputException("User does not exist"));
        user.addRole(role, timeProvider.now());
        userRepository.save(user);
    }
}
