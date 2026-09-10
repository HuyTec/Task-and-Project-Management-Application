package com.taskmanagement.dto.review;
import java.time.LocalDateTime;
import java.util.List;
import com.taskmanagement.model.ReviewDecision;
import com.taskmanagement.model.SubmissionReviewStatus;
public record SubmissionReviewResponse(Long id, Long submissionId, String reviewerUsername,
        SubmissionReviewStatus status, ReviewDecision decision, String message, LocalDateTime submittedAt,
        List<ReviewCriterionResponse> criteria) { }
