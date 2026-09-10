package com.taskmanagement.repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.taskmanagement.model.SubmissionReview;
import com.taskmanagement.model.SubmissionReviewStatus;
public interface SubmissionReviewRepository extends JpaRepository<SubmissionReview, Long> {
    Optional<SubmissionReview> findBySubmissionIdAndReviewerId(Long submissionId, Long reviewerId);
    List<SubmissionReview> findBySubmissionIdOrderByCreatedAtAsc(Long submissionId);
    boolean existsBySubmissionIdAndStatus(Long submissionId, SubmissionReviewStatus status);
    List<SubmissionReview> findBySubmissionTaskIdAndStatusOrderBySubmittedAtAsc(Long taskId, SubmissionReviewStatus status);
}
