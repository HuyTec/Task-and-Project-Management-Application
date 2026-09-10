package com.taskmanagement.service.submission;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import com.taskmanagement.dto.Response;
import com.taskmanagement.dto.submission.CreateLinkEvidenceRequest;
import com.taskmanagement.dto.submission.EvidenceResponse;
import com.taskmanagement.dto.submission.CompleteFileEvidenceRequest;
import com.taskmanagement.dto.submission.FileUploadInitiatedResponse;
import com.taskmanagement.dto.submission.InitiateFileEvidenceRequest;
import com.taskmanagement.dto.submission.SubmissionResponse;
import com.taskmanagement.exception.BadRequestException;
import com.taskmanagement.exception.ConflictException;
import com.taskmanagement.exception.ForbiddenException;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.event.TaskCacheEvictEvent;
import com.taskmanagement.model.AssignmentStatus;
import com.taskmanagement.model.EvidenceProvider;
import com.taskmanagement.model.EvidenceType;
import com.taskmanagement.model.ProjectMember;
import com.taskmanagement.model.ProjectRole;
import com.taskmanagement.model.Submission;
import com.taskmanagement.model.SubmissionStatus;
import com.taskmanagement.model.Task;
import com.taskmanagement.model.TaskAssignment;
import com.taskmanagement.model.TaskEvidence;
import com.taskmanagement.model.TaskStatus;
import com.taskmanagement.model.UploadStatus;
import com.taskmanagement.model.SubmissionCriterionSnapshot;
import com.taskmanagement.repository.MemberRepository;
import com.taskmanagement.repository.SubmissionRepository;
import com.taskmanagement.repository.SubmissionCriterionSnapshotRepository;
import com.taskmanagement.repository.TaskAcceptanceCriterionRepository;
import com.taskmanagement.repository.TaskAssignmentRepository;
import com.taskmanagement.repository.TaskEvidenceRepository;
import com.taskmanagement.repository.TaskRepository;
import com.taskmanagement.security.CustomUserDetails;
import com.taskmanagement.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SubmissionService {
    public static final int MAX_EVIDENCE_COUNT = 10;
    public static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "docx", "pptx", "xlsx", "png", "jpg", "zip"
    );

    private final SubmissionRepository submissionRepository;
    private final SubmissionCriterionSnapshotRepository snapshotRepository;
    private final TaskEvidenceRepository evidenceRepository;
    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository assignmentRepository;
    private final TaskAcceptanceCriterionRepository criterionRepository;
    private final MemberRepository memberRepository;
    private final SecurityUtils securityUtils;
    private final EvidenceStorage evidenceStorage;
    private final ApplicationEventPublisher eventPublisher;

    public Response<SubmissionResponse> create(Long taskId) {
        Task task = requireLockedProjectTask(taskId);
        ProjectMember actor = requireCurrentMember(task);
        requireActiveAssignee(task, actor);
        requireSubmissionCreationState(task);
        if (submissionRepository.existsByTaskIdAndStatus(taskId, SubmissionStatus.DRAFT)) {
            throw new ConflictException("Task already has a draft submission");
        }

        Submission submission = new Submission();
        submission.setTask(task);
        submission.setAssignee(actor);
        submission.setSequenceNumber(submissionRepository.findMaxSequenceNumber(taskId) + 1);
        submission.setStatus(SubmissionStatus.DRAFT);
        Submission saved = submissionRepository.saveAndFlush(submission);
        return Response.success(toResponse(saved, List.of()), "Submission draft created successfully!");
    }

    @Transactional(readOnly = true)
    public Response<List<SubmissionResponse>> listForTask(Long taskId) {
        Task task = requireProjectTask(taskId);
        ProjectMember actor = requireCurrentMember(task);
        List<SubmissionResponse> submissions = submissionRepository
                .findByTaskIdOrderBySequenceNumberDesc(taskId)
                .stream()
                .filter(submission -> canRead(submission, actor))
                .map(this::toResponseWithEvidence)
                .toList();
        return Response.success(submissions, "Submissions retrieved successfully!");
    }

    @Transactional(readOnly = true)
    public Response<SubmissionResponse> get(Long submissionId) {
        Submission submission = requireSubmission(submissionId);
        ProjectMember actor = requireCurrentMember(submission.getTask());
        requireReadPermission(submission, actor);
        return Response.success(toResponseWithEvidence(submission), "Submission retrieved successfully!");
    }

    public Response<EvidenceResponse> addLink(
            Long submissionId,
            CreateLinkEvidenceRequest request
    ) {
        Submission submission = requireOwnedDraft(submissionId);
        requireEvidenceCapacity(submissionId);
        validateLink(request);

        TaskEvidence evidence = new TaskEvidence();
        evidence.setSubmission(submission);
        evidence.setEvidenceType(request.evidenceType());
        evidence.setProvider(request.provider());
        evidence.setDisplayName(request.displayName().trim());
        evidence.setUrl(normalizeUrl(request.url()));
        TaskEvidence saved = evidenceRepository.saveAndFlush(evidence);
        return Response.success(toEvidenceResponse(saved), "Evidence link added successfully!");
    }

    public Response<Void> deleteEvidence(Long submissionId, Long evidenceId) {
        requireOwnedDraft(submissionId);
        TaskEvidence evidence = evidenceRepository.findByIdAndSubmissionId(evidenceId, submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence not found!"));
        evidenceRepository.delete(evidence);
        return Response.success(null, "Evidence deleted successfully!");
    }

    public Response<FileUploadInitiatedResponse> initiateFile(
            Long submissionId,
            InitiateFileEvidenceRequest request
    ) {
        Submission submission = requireOwnedDraft(submissionId);
        requireEvidenceCapacity(submissionId);
        validateFile(request);

        String normalizedName = request.fileName().trim();
        String storageKey = "task-evidence/%d/%s-%s".formatted(
                submissionId,
                UUID.randomUUID(),
                normalizedName.replaceAll("[^A-Za-z0-9._-]", "_")
        );
        EvidenceStorage.PresignedUpload upload = evidenceStorage.createUpload(
                storageKey,
                request.contentType().trim(),
                request.fileSize()
        );

        TaskEvidence evidence = new TaskEvidence();
        evidence.setSubmission(submission);
        evidence.setEvidenceType(EvidenceType.UPLOADED_FILE);
        evidence.setProvider(EvidenceProvider.OBJECT_STORAGE);
        evidence.setDisplayName(normalizedName);
        evidence.setStorageKey(storageKey);
        evidence.setContentType(request.contentType().trim());
        evidence.setFileSize(request.fileSize());
        evidence.setUploadStatus(UploadStatus.PENDING);
        TaskEvidence saved = evidenceRepository.saveAndFlush(evidence);
        return Response.success(
                new FileUploadInitiatedResponse(
                        toEvidenceResponse(saved), upload.uploadUrl(), upload.requiredHeaders()
                ),
                "File upload initiated successfully!"
        );
    }

    public Response<EvidenceResponse> completeFile(
            Long submissionId,
            CompleteFileEvidenceRequest request
    ) {
        requireOwnedDraft(submissionId);
        TaskEvidence evidence = evidenceRepository
                .findByIdAndSubmissionId(request.evidenceId(), submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence not found!"));
        if (evidence.getEvidenceType() != EvidenceType.UPLOADED_FILE
                || evidence.getUploadStatus() != UploadStatus.PENDING) {
            throw new ConflictException("Only a PENDING file upload can be completed");
        }
        EvidenceStorage.StoredObject stored = evidenceStorage.verifyUpload(evidence.getStorageKey());
        if (stored.fileSize() != evidence.getFileSize()) {
            evidence.setUploadStatus(UploadStatus.FAILED);
            evidenceRepository.save(evidence);
            throw new BadRequestException("Uploaded file size does not match the initiated upload");
        }
        evidence.setContentType(stored.contentType());
        evidence.setUploadStatus(UploadStatus.READY);
        TaskEvidence saved = evidenceRepository.saveAndFlush(evidence);
        return Response.success(toEvidenceResponse(saved), "File evidence completed successfully!");
    }

    public Response<SubmissionResponse> submit(Long submissionId) {
        Submission submission = requireOwnedDraft(submissionId);
        Task task = submission.getTask();
        requireActiveAssignee(task, submission.getAssignee());
        if (task.getStatus() != TaskStatus.IN_PROGRESS
                && task.getStatus() != TaskStatus.CHANGES_REQUESTED) {
            throw new ConflictException(
                    "Submission can only be submitted while task is IN_PROGRESS or CHANGES_REQUESTED"
            );
        }
        if (!criterionRepository.existsByTaskId(task.getId())) {
            throw new BadRequestException("Task requires at least one acceptance criterion before review");
        }
        if (task.getReviewer() == null) {
            throw new BadRequestException("A designated reviewer is required before submission");
        }
        if (task.getReviewer().getId().equals(submission.getAssignee().getId())) {
            throw new ConflictException("Reviewer must be different from the assignee");
        }
        List<TaskEvidence> evidence = evidenceRepository.findBySubmissionIdOrderByCreatedAtAsc(submissionId);
        if (evidence.isEmpty()) {
            throw new BadRequestException("Submission requires at least one evidence item");
        }
        if (evidenceRepository.existsBySubmissionIdAndUploadStatusNot(submissionId, UploadStatus.READY)) {
            throw new ConflictException("All file uploads must be READY before submission");
        }

        snapshotRepository.saveAll(criterionRepository.findByTaskIdOrderByPositionAsc(task.getId()).stream()
                .map(criterion -> {
                    SubmissionCriterionSnapshot snapshot = new SubmissionCriterionSnapshot();
                    snapshot.setSubmission(submission);
                    snapshot.setSourceCriterionId(criterion.getId());
                    snapshot.setContent(criterion.getContent());
                    snapshot.setPosition(criterion.getPosition());
                    return snapshot;
                }).toList());
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(LocalDateTime.now());
        task.setStatus(TaskStatus.IN_REVIEW);
        taskRepository.save(task);
        Submission saved = submissionRepository.saveAndFlush(submission);
        memberRepository.findByProjectId(task.getProject().getId()).forEach(member ->
                eventPublisher.publishEvent(new TaskCacheEvictEvent(member.getUser().getId(), task.getId()))
        );
        return Response.success(toResponse(saved, evidence), "Submission sent for review successfully!");
    }

    private Submission requireOwnedDraft(Long submissionId) {
        Submission submission = requireLockedSubmission(submissionId);
        ProjectMember actor = requireCurrentMember(submission.getTask());
        if (!submission.getAssignee().getId().equals(actor.getId())) {
            throw new ForbiddenException("Only the submission owner can modify evidence");
        }
        if (submission.getStatus() != SubmissionStatus.DRAFT) {
            throw new ConflictException("Submitted evidence is immutable");
        }
        return submission;
    }

    private void requireEvidenceCapacity(Long submissionId) {
        if (evidenceRepository.countBySubmissionId(submissionId) >= MAX_EVIDENCE_COUNT) {
            throw new BadRequestException("A submission can contain at most 10 evidence items");
        }
    }

    private void validateFile(InitiateFileEvidenceRequest request) {
        if (request.fileSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size must not exceed 25 MiB");
        }
        String fileName = request.fileName().trim();
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException(
                    "File type is not allowed; use pdf, docx, pptx, xlsx, png, jpg or zip"
            );
        }
    }

    private void validateLink(CreateLinkEvidenceRequest request) {
        if (request.evidenceType() == EvidenceType.UPLOADED_FILE) {
            throw new BadRequestException("Uploaded files must use the file evidence endpoint");
        }
        if ((request.evidenceType() == EvidenceType.GITHUB_COMMIT
                || request.evidenceType() == EvidenceType.GITHUB_PR)
                && request.provider() != EvidenceProvider.GITHUB) {
            throw new BadRequestException("GitHub evidence requires GITHUB provider");
        }
        URI uri = parseHttpUrl(request.url());
        if (request.provider() == EvidenceProvider.GITHUB
                && !"github.com".equalsIgnoreCase(uri.getHost())) {
            throw new BadRequestException("GitHub evidence URL must use github.com");
        }
        if (request.provider() == EvidenceProvider.OBJECT_STORAGE) {
            throw new BadRequestException("Link evidence cannot use OBJECT_STORAGE provider");
        }
    }

    private String normalizeUrl(String value) {
        return parseHttpUrl(value).normalize().toString();
    }

    private URI parseHttpUrl(String value) {
        try {
            URI uri = new URI(value.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw new BadRequestException("Evidence URL must be an absolute HTTP(S) URL");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new BadRequestException("Evidence URL is invalid");
        }
    }

    private void requireSubmissionCreationState(Task task) {
        if (task.getStatus() != TaskStatus.IN_PROGRESS
                && task.getStatus() != TaskStatus.CHANGES_REQUESTED) {
            throw new ConflictException(
                    "Submission draft can only be created while task is IN_PROGRESS or CHANGES_REQUESTED"
            );
        }
    }

    private Task requireLockedProjectTask(Long taskId) {
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found!"));
        if (task.getProject() == null) throw new BadRequestException("Submission requires a project task");
        return task;
    }

    private Task requireProjectTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found!"));
        if (task.getProject() == null) throw new BadRequestException("Submission requires a project task");
        return task;
    }

    private Submission requireSubmission(Long submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found!"));
    }

    private Submission requireLockedSubmission(Long submissionId) {
        return submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found!"));
    }

    private ProjectMember requireCurrentMember(Task task) {
        CustomUserDetails currentUser = securityUtils.getCurrentUser();
        return memberRepository.findByProjectIdAndUserId(task.getProject().getId(), currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found!"));
    }

    private void requireActiveAssignee(Task task, ProjectMember actor) {
        TaskAssignment assignment = assignmentRepository
                .findByTaskIdAndStatus(task.getId(), AssignmentStatus.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("Task has no active assignee"));
        if (!assignment.getAssignee().getId().equals(actor.getId())) {
            throw new ForbiddenException("Only the active assignee can create or submit work");
        }
    }

    private boolean canRead(Submission submission, ProjectMember actor) {
        return submission.getAssignee().getId().equals(actor.getId())
                || (submission.getTask().getReviewer() != null
                && submission.getTask().getReviewer().getId().equals(actor.getId()));
    }

    private void requireReadPermission(Submission submission, ProjectMember actor) {
        if (!canRead(submission, actor)) {
            throw new ForbiddenException("Evidence is visible only to the assignee and eligible reviewers");
        }
    }

    private SubmissionResponse toResponseWithEvidence(Submission submission) {
        return toResponse(
                submission,
                evidenceRepository.findBySubmissionIdOrderByCreatedAtAsc(submission.getId())
        );
    }

    private SubmissionResponse toResponse(Submission submission, List<TaskEvidence> evidence) {
        return new SubmissionResponse(
                submission.getId(), submission.getTask().getId(), submission.getSequenceNumber(),
                submission.getStatus(), submission.getAssignee().getUser().getUsername(),
                submission.getCreatedAt(), submission.getSubmittedAt(),
                evidence.stream().map(this::toEvidenceResponse).toList()
        );
    }

    private EvidenceResponse toEvidenceResponse(TaskEvidence evidence) {
        return new EvidenceResponse(
                evidence.getId(), evidence.getEvidenceType(), evidence.getProvider(),
                evidence.getDisplayName(), evidence.getUrl(), evidence.getContentType(),
                evidence.getFileSize(), evidence.getUploadStatus(), evidence.getCreatedAt()
        );
    }
}
