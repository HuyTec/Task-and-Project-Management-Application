package com.taskmanagement.dto.task;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateAcceptanceCriterionRequest(
        @Size(max = 1000, message = "Criterion content must be at most 1000 characters")
        String content,
        @PositiveOrZero(message = "Criterion position must be zero or positive")
        Integer position
) {
}
