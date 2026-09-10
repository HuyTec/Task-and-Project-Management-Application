package com.taskmanagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "submission_criterion_reviews")
@Getter @Setter
public class SubmissionCriterionReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "submission_review_id", nullable = false) private SubmissionReview submissionReview;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "criterion_snapshot_id", nullable = false) private SubmissionCriterionSnapshot criterionSnapshot;
    @Enumerated(EnumType.STRING) @Column(length = 32) private CriterionReviewResult result;
    @Column(length = 2000) private String comment;
}
