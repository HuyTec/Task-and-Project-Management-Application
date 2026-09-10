package com.taskmanagement.dto.review;
import java.util.List;
import com.taskmanagement.dto.submission.SubmissionResponse;
public record SubmissionReviewScreenResponse(SubmissionResponse submission, String taskTitle,
        String designatedReviewerUsername, boolean currentUserIsReviewer, SubmissionReviewResponse currentReview,
        List<SubmissionReviewResponse> history) { }
