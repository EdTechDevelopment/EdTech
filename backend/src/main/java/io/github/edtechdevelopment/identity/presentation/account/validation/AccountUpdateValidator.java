package io.github.edtechdevelopment.identity.presentation.account.validation;

import io.github.edtechdevelopment.identity.presentation.account.model.request.UpdateCurrentUserRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class AccountUpdateValidator implements ConstraintValidator<ValidAccountUpdate, UpdateCurrentUserRequest> {

    @Override
    public boolean isValid(UpdateCurrentUserRequest request, ConstraintValidatorContext context) {
        return request == null
                || request.email() != null
                || request.firstName() != null
                || request.lastName() != null;
    }
}
