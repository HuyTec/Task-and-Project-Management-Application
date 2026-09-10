package com.taskmanagement.dto.review;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
public record SaveSubmissionReviewRequest(@Size(max = 5000) String message, @Valid List<ReviewCriterionRequest> criteria) { }
