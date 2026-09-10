package com.taskmanagement.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.taskmanagement.model.SubmissionCriterionSnapshot;
public interface SubmissionCriterionSnapshotRepository extends JpaRepository<SubmissionCriterionSnapshot, Long> {
    List<SubmissionCriterionSnapshot> findBySubmissionIdOrderByPositionAsc(Long submissionId);
}
