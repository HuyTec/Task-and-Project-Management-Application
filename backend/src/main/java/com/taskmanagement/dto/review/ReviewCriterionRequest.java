package com.taskmanagement.dto.review;
import com.taskmanagement.model.CriterionReviewResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record ReviewCriterionRequest(@NotNull Long criterionSnapshotId, @NotNull CriterionReviewResult result,
        @Size(max = 2000) String comment) { }
