package com.taskmanagement.service.submission;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.taskmanagement.dto.Response;
import com.taskmanagement.dto.review.*;
import com.taskmanagement.dto.submission.EvidenceResponse;
import com.taskmanagement.dto.submission.SubmissionResponse;
import com.taskmanagement.event.TaskCacheEvictEvent;
import com.taskmanagement.exception.BadRequestException;
import com.taskmanagement.exception.ConflictException;
import com.taskmanagement.exception.ForbiddenException;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.model.*;
import com.taskmanagement.repository.*;
import com.taskmanagement.security.CustomUserDetails;
import com.taskmanagement.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SubmissionReviewService {
    private final SubmissionRepository submissionRepository;
    private final SubmissionCriterionSnapshotRepository snapshotRepository;
    private final SubmissionReviewRepository reviewRepository;
    private final SubmissionCriterionReviewRepository criterionReviewRepository;
    private final TaskEvidenceRepository evidenceRepository;
    private final TaskRepository taskRepository;
    private final MemberRepository memberRepository;
    private final SecurityUtils securityUtils;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Response<SubmissionReviewScreenResponse> screen(Long submissionId) {
        Submission submission = requireSubmission(submissionId);
        ProjectMember actor = currentMember(submission.getTask());
        requireEvidenceRead(submission, actor);
        List<SubmissionReviewResponse> history = reviewRepository.findBySubmissionIdOrderByCreatedAtAsc(submissionId)
                .stream().map(this::response).toList();
        SubmissionReviewResponse current = history.stream()
                .filter(review -> review.reviewerUsername().equals(actor.getUser().getUsername())
                        && review.status() == SubmissionReviewStatus.DRAFT).findFirst().orElse(null);
        boolean currentUserIsReviewer = submission.getTask().getReviewer() != null
                && submission.getTask().getReviewer().getId().equals(actor.getId());
        return Response.success(new SubmissionReviewScreenResponse(submissionResponse(submission),
                submission.getTask().getTitle(), submission.getTask().getReviewer() == null ? null
                        : submission.getTask().getReviewer().getUser().getUsername(), currentUserIsReviewer, current, history),
                "Submission review retrieved successfully!");
    }

    public Response<SubmissionReviewResponse> saveDraft(Long submissionId, SaveSubmissionReviewRequest request) {
        Submission submission = requireReviewableSubmission(submissionId);
        ProjectMember reviewer = requireDesignatedReviewer(submission);
        SubmissionReview review = reviewRepository.findBySubmissionIdAndReviewerId(submissionId, reviewer.getId())
                .orElseGet(() -> newDraft(submission, reviewer));
        if (review.getStatus() != SubmissionReviewStatus.DRAFT) throw new ConflictException("Submitted review is immutable");
        review.setMessage(blankToNull(request.message()));
        saveCriterionResults(review, request.criteria());
        return Response.success(response(reviewRepository.saveAndFlush(review)), "Review draft saved successfully!");
    }

    public Response<SubmissionReviewResponse> submit(Long submissionId, SubmitSubmissionReviewRequest request) {
        Submission submission = requireReviewableSubmission(submissionId);
        ProjectMember reviewer = requireDesignatedReviewer(submission);
        SubmissionReview review = reviewRepository.findBySubmissionIdAndReviewerId(submissionId, reviewer.getId())
                .orElseGet(() -> newDraft(submission, reviewer));
        if (review.getStatus() != SubmissionReviewStatus.DRAFT) throw new ConflictException("A decision has already been submitted for this reviewer");
        List<SubmissionCriterionSnapshot> snapshots = snapshotRepository.findBySubmissionIdOrderByPositionAsc(submissionId);
        if (snapshots.isEmpty()) throw new ConflictException("Submission is missing its criterion snapshot");
        if (request.criteria().size() != snapshots.size()) throw new BadRequestException("Review every criterion before submitting a decision");
        if (request.decision() == ReviewDecision.CHANGES_REQUESTED && blankToNull(request.message()) == null) {
            throw new BadRequestException("Changes requested requires an overall comment");
        }
        saveCriterionResults(review, request.criteria());
        Map<Long, SubmissionCriterionReview> results = criterionReviewRepository.findBySubmissionReviewId(review.getId()).stream()
                .collect(Collectors.toMap(item -> item.getCriterionSnapshot().getId(), Function.identity()));
        if (snapshots.stream().anyMatch(snapshot -> !results.containsKey(snapshot.getId()) || results.get(snapshot.getId()).getResult() == null)) {
            throw new BadRequestException("Review every criterion before submitting a decision");
        }
        if (request.decision() == ReviewDecision.APPROVED && results.values().stream().anyMatch(item -> item.getResult() != CriterionReviewResult.PASSED)) {
            throw new BadRequestException("Approval requires every criterion to be marked PASSED");
        }
        review.setDecision(request.decision());
        review.setMessage(blankToNull(request.message()));
        review.setStatus(SubmissionReviewStatus.SUBMITTED);
        review.setSubmittedAt(LocalDateTime.now());
        Task task = submission.getTask();
        task.setStatus(request.decision() == ReviewDecision.APPROVED ? TaskStatus.DONE : TaskStatus.CHANGES_REQUESTED);
        taskRepository.save(task);
        SubmissionReview saved = reviewRepository.saveAndFlush(review);
        memberRepository.findByProjectId(task.getProject().getId()).forEach(member ->
                eventPublisher.publishEvent(new TaskCacheEvictEvent(member.getUser().getId(), task.getId())));
        return Response.success(response(saved), "Review decision submitted successfully!");
    }

    private void saveCriterionResults(SubmissionReview review, List<ReviewCriterionRequest> requests) {
        if (requests == null) return;
        Map<Long, SubmissionCriterionSnapshot> snapshots = snapshotRepository
                .findBySubmissionIdOrderByPositionAsc(review.getSubmission().getId()).stream()
                .collect(Collectors.toMap(SubmissionCriterionSnapshot::getId, Function.identity()));
        for (ReviewCriterionRequest request : requests) {
            SubmissionCriterionSnapshot snapshot = snapshots.get(request.criterionSnapshotId());
            if (snapshot == null) throw new BadRequestException("Criterion does not belong to this submission");
            SubmissionCriterionReview item = criterionReviewRepository
                    .findBySubmissionReviewIdAndCriterionSnapshotId(review.getId(), snapshot.getId()).orElseGet(() -> {
                        SubmissionCriterionReview created = new SubmissionCriterionReview();
                        created.setSubmissionReview(review); created.setCriterionSnapshot(snapshot); return created;
                    });
            item.setResult(request.result()); item.setComment(blankToNull(request.comment()));
            criterionReviewRepository.save(item);
        }
    }

    private SubmissionReview newDraft(Submission submission, ProjectMember reviewer) {
        SubmissionReview review = new SubmissionReview(); review.setSubmission(submission); review.setReviewer(reviewer);
        review.setStatus(SubmissionReviewStatus.DRAFT); return reviewRepository.saveAndFlush(review);
    }
    private Submission requireSubmission(Long id) { return submissionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Submission not found!")); }
    private Submission requireReviewableSubmission(Long id) { Submission submission = requireSubmission(id); if (submission.getStatus() != SubmissionStatus.SUBMITTED || submission.getTask().getStatus() != TaskStatus.IN_REVIEW) throw new ConflictException("Submission is not awaiting review"); return submission; }
    private ProjectMember currentMember(Task task) { CustomUserDetails user = securityUtils.getCurrentUser(); return memberRepository.findByProjectIdAndUserId(task.getProject().getId(), user.getId()).orElseThrow(() -> new ResourceNotFoundException("Submission not found!")); }
    private ProjectMember requireDesignatedReviewer(Submission submission) { ProjectMember actor = currentMember(submission.getTask()); if (submission.getTask().getReviewer() == null || !submission.getTask().getReviewer().getId().equals(actor.getId())) throw new ForbiddenException("Only the designated reviewer can review this submission"); return actor; }
    private void requireEvidenceRead(Submission submission, ProjectMember actor) { if (!submission.getAssignee().getId().equals(actor.getId()) && (submission.getTask().getReviewer() == null || !submission.getTask().getReviewer().getId().equals(actor.getId()))) throw new ForbiddenException("Evidence is visible only to the assignee and designated reviewer"); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private SubmissionReviewResponse response(SubmissionReview review) {
        Map<Long, SubmissionCriterionReview> results = criterionReviewRepository.findBySubmissionReviewId(review.getId()).stream().collect(Collectors.toMap(item -> item.getCriterionSnapshot().getId(), Function.identity()));
        List<ReviewCriterionResponse> criteria = snapshotRepository.findBySubmissionIdOrderByPositionAsc(review.getSubmission().getId()).stream().map(snapshot -> { SubmissionCriterionReview result = results.get(snapshot.getId()); return new ReviewCriterionResponse(snapshot.getId(), snapshot.getContent(), snapshot.getPosition(), result == null ? null : result.getResult(), result == null ? null : result.getComment()); }).toList();
        return new SubmissionReviewResponse(review.getId(), review.getSubmission().getId(), review.getReviewer().getUser().getUsername(), review.getStatus(), review.getDecision(), review.getMessage(), review.getSubmittedAt(), criteria);
    }
    private SubmissionResponse submissionResponse(Submission submission) { return new SubmissionResponse(submission.getId(), submission.getTask().getId(), submission.getSequenceNumber(), submission.getStatus(), submission.getAssignee().getUser().getUsername(), submission.getCreatedAt(), submission.getSubmittedAt(), evidenceRepository.findBySubmissionIdOrderByCreatedAtAsc(submission.getId()).stream().map(e -> new EvidenceResponse(e.getId(), e.getEvidenceType(), e.getProvider(), e.getDisplayName(), e.getUrl(), e.getContentType(), e.getFileSize(), e.getUploadStatus(), e.getCreatedAt())).toList()); }
}
