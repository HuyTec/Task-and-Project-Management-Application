package com.taskmanagement.dto.review;
import com.taskmanagement.model.CriterionReviewResult;
public record ReviewCriterionResponse(Long criterionSnapshotId, String content, Integer position,
        CriterionReviewResult result, String comment) { }
