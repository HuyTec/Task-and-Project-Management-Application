package com.taskmanagement.repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.taskmanagement.model.SubmissionCriterionReview;
public interface SubmissionCriterionReviewRepository extends JpaRepository<SubmissionCriterionReview, Long> {
    List<SubmissionCriterionReview> findBySubmissionReviewId(Long submissionReviewId);
    Optional<SubmissionCriterionReview> findBySubmissionReviewIdAndCriterionSnapshotId(Long reviewId, Long snapshotId);
}
