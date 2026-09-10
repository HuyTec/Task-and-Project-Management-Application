package com.taskmanagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "submission_criterion_snapshots")
@Getter @Setter
public class SubmissionCriterionSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "submission_id", nullable = false) private Submission submission;
    @Column(name = "source_criterion_id") private Long sourceCriterionId;
    @Column(nullable = false, length = 1000) private String content;
    @Column(nullable = false) private Integer position;
}
