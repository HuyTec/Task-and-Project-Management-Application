package com.taskmanagement.dto.review;
import java.util.List;
import com.taskmanagement.model.ReviewDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record SubmitSubmissionReviewRequest(@NotNull ReviewDecision decision, @Size(max = 5000) String message,
        @Valid @NotNull List<ReviewCriterionRequest> criteria) { }
