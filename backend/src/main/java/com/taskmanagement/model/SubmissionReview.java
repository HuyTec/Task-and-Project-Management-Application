package com.taskmanagement.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "submission_reviews")
@Getter @Setter
public class SubmissionReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "submission_id", nullable = false) private Submission submission;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reviewer_id", nullable = false) private ProjectMember reviewer;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private SubmissionReviewStatus status;
    @Enumerated(EnumType.STRING) @Column(length = 32) private ReviewDecision decision;
    @Column(length = 5000) private String message;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "submitted_at") private LocalDateTime submittedAt;
    @PrePersist void created() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
