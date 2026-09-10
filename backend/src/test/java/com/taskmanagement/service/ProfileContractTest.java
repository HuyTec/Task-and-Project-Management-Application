package com.taskmanagement.service;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import com.taskmanagement.dto.user.UpdateUserRequest;

class ProfileContractTest {
    @Test void genericProfileEndpointRejectsPasswordBypass() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new UpdateUserRequest(null, null, null, "new-password", null));
            assertThat(violations).anyMatch(item -> item.getPropertyPath().toString().equals("password"));
        }
    }
}
