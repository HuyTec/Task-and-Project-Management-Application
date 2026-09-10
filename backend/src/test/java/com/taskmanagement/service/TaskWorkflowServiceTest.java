package com.taskmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.taskmanagement.dto.task.AssignTaskRequest;
import com.taskmanagement.dto.task.CreateProjectTaskRequest;
import com.taskmanagement.dto.task.RequestChangesRequest;
import com.taskmanagement.dto.task.UpdateAcceptanceCriterionRequest;
import com.taskmanagement.exception.BadRequestException;
import com.taskmanagement.exception.DuplicatedResourceException;
import com.taskmanagement.exception.ForbiddenException;
import com.taskmanagement.exception.ConflictException;
import com.taskmanagement.mapper.TaskWorkflowMapper;
import com.taskmanagement.mapper.TaskMapper;
import com.taskmanagement.model.AssignmentStatus;
import com.taskmanagement.model.AssignmentType;
import com.taskmanagement.model.Project;
import com.taskmanagement.model.ProjectMember;
import com.taskmanagement.model.ProjectRole;
import com.taskmanagement.model.ProjectStatus;
import com.taskmanagement.model.TaskPriority;
import com.taskmanagement.model.ReviewDecision;
import com.taskmanagement.model.Task;
import com.taskmanagement.model.TaskAcceptanceCriterion;
import com.taskmanagement.model.TaskAssignment;
import com.taskmanagement.model.TaskReview;
import com.taskmanagement.model.TaskStatus;
import com.taskmanagement.model.Submission;
import com.taskmanagement.model.SubmissionStatus;
import com.taskmanagement.model.User;
import com.taskmanagement.repository.MemberRepository;
import com.taskmanagement.repository.TaskAcceptanceCriterionRepository;
import com.taskmanagement.repository.TaskAssignmentRepository;
import com.taskmanagement.repository.TaskRepository;
import com.taskmanagement.repository.TaskReviewRepository;
import com.taskmanagement.repository.SubmissionRepository;
import com.taskmanagement.security.CustomUserDetails;
import com.taskmanagement.service.task.TaskWorkflowServiceImpl;
import com.taskmanagement.utils.SecurityUtils;

@ExtendWith(MockitoExtension.class)
class TaskWorkflowServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskAssignmentRepository assignmentRepository;
    @Mock private TaskAcceptanceCriterionRepository criterionRepository;
    @Mock private TaskReviewRepository reviewRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private TaskWorkflowMapper workflowMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private SecurityUtils securityUtils;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CustomUserDetails currentUser;

    @InjectMocks
    private TaskWorkflowServiceImpl service;

    @Test
    void ownerCreatesCompleteProjectTaskInOneCommand() {
        Project project = project(ProjectStatus.PLANNING);
        ProjectMember owner = actor(project, 7L, ProjectRole.OWNER);
        ProjectMember assignee = actor(project, 9L, ProjectRole.MEMBER);
        CreateProjectTaskRequest request = createRequest("member");
        stubCurrentActor(project, owner);
        when(memberRepository.findByProjectIdAndUserUsername(12L, "member"))
                .thenReturn(Optional.of(assignee));
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(42L);
            return task;
        });
        when(assignmentRepository.saveAndFlush(any(TaskAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createProjectTask(12L, request);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).saveAndFlush(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getProject()).isSameAs(project);
        assertThat(taskCaptor.getValue().getUser()).isSameAs(owner.getUser());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(TaskStatus.TODO);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TaskAcceptanceCriterion>> criteriaCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(criterionRepository).saveAll(criteriaCaptor.capture());
        assertThat(criteriaCaptor.getValue())
                .extracting(TaskAcceptanceCriterion::getPosition)
                .containsExactly(0, 1);
        assertThat(criteriaCaptor.getValue())
                .extracting(TaskAcceptanceCriterion::getContent)
                .containsExactly("First result", "Second result");

        ArgumentCaptor<TaskAssignment> assignmentCaptor = ArgumentCaptor.forClass(TaskAssignment.class);
        verify(assignmentRepository).saveAndFlush(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue().getType()).isEqualTo(AssignmentType.ASSIGNED);
        assertThat(assignmentCaptor.getValue().getAssignee()).isSameAs(assignee);
        assertThat(assignmentCaptor.getValue().getAssignedBy()).isSameAs(owner);
    }

    @Test
    void memberIsRejectedBeforeAssigneeLookupOrPersistence() {
        Project project = project(ProjectStatus.ACTIVE);
        ProjectMember member = actor(project, 7L, ProjectRole.MEMBER);
        stubCurrentActor(project, member);

        assertThatThrownBy(() -> service.createProjectTask(12L, createRequest("someone")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Task management requires OWNER or MANAGER role");

        verify(memberRepository, never()).findByProjectIdAndUserUsername(any(), any());
        verify(taskRepository, never()).saveAndFlush(any(Task.class));
    }

    @Test
    void closedProjectRejectsTaskPlanningBeforePersistence() {
        Project project = project(ProjectStatus.ON_HOLD);
        ProjectMember manager = actor(project, 7L, ProjectRole.MANAGER);
        stubCurrentActor(project, manager);

        assertThatThrownBy(() -> service.createProjectTask(12L, createRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Project tasks can only be created while project is PLANNING or ACTIVE");

        verify(taskRepository, never()).saveAndFlush(any(Task.class));
    }

    @Test
    void dueDateOutsideProjectScheduleIsRejectedBeforePersistence() {
        Project project = project(ProjectStatus.ACTIVE);
        ProjectMember manager = actor(project, 7L, ProjectRole.MANAGER);
        stubCurrentActor(project, manager);
        CreateProjectTaskRequest request = new CreateProjectTaskRequest(
                "Task", "Description", TaskPriority.HIGH,
                LocalDate.of(2026, 9, 1), List.of("Result"), null
        );

        assertThatThrownBy(() -> service.createProjectTask(12L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Task due date must stay within the project schedule");

        verify(taskRepository, never()).saveAndFlush(any(Task.class));
    }

    @Test
    void viewerCannotBeInitialAssignee() {
        Project project = project(ProjectStatus.ACTIVE);
        ProjectMember manager = actor(project, 7L, ProjectRole.MANAGER);
        ProjectMember viewer = actor(project, 9L, ProjectRole.VIEWER);
        stubCurrentActor(project, manager);
        when(memberRepository.findByProjectIdAndUserUsername(12L, "viewer"))
                .thenReturn(Optional.of(viewer));

        assertThatThrownBy(() -> service.createProjectTask(12L, createRequest("viewer")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("VIEWER cannot be assigned project tasks");

        verify(taskRepository, never()).saveAndFlush(any(Task.class));
    }

    @Test
    void memberCanClaimAnUnassignedTodoTask() {
        Task task = task(42L, TaskStatus.TODO);
        ProjectMember member = actor(task, 7L, ProjectRole.MEMBER);
        stubLockedTaskAndActor(task, member);
        when(assignmentRepository.existsByTaskIdAndStatus(42L, AssignmentStatus.ACTIVE))
                .thenReturn(false);
        when(assignmentRepository.saveAndFlush(any(TaskAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.claim(42L);

        ArgumentCaptor<TaskAssignment> assignmentCaptor = ArgumentCaptor.forClass(TaskAssignment.class);
        verify(assignmentRepository).saveAndFlush(assignmentCaptor.capture());
        TaskAssignment assignment = assignmentCaptor.getValue();
        assertThat(assignment.getAssignee()).isSameAs(member);
        assertThat(assignment.getAssignedBy()).isNull();
        assertThat(assignment.getType()).isEqualTo(AssignmentType.CLAIMED);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ACTIVE);
        assertThat(assignment.getAssignedAt()).isNotNull();
    }

    @Test
    void claimRejectsASecondActiveAssignment() {
        Task task = task(42L, TaskStatus.TODO);
        ProjectMember member = actor(task, 7L, ProjectRole.MEMBER);
        stubLockedTaskAndActor(task, member);
        when(assignmentRepository.existsByTaskIdAndStatus(42L, AssignmentStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.claim(42L))
                .isInstanceOf(DuplicatedResourceException.class)
                .hasMessage("Task already has an active assignment");

        verify(assignmentRepository, never()).saveAndFlush(any(TaskAssignment.class));
    }

    @Test
    void managerCannotAssignTaskToViewer() {
        Task task = task(42L, TaskStatus.TODO);
        ProjectMember manager = actor(task, 7L, ProjectRole.MANAGER);
        ProjectMember viewer = actor(task, 9L, ProjectRole.VIEWER);
        stubLockedTaskAndActor(task, manager);
        when(memberRepository.findByProjectIdAndUserUsername(12L, "viewer"))
                .thenReturn(Optional.of(viewer));

        assertThatThrownBy(() -> service.assign(42L, new AssignTaskRequest("viewer")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("VIEWER cannot be assigned project tasks");

        verify(assignmentRepository, never()).saveAndFlush(any(TaskAssignment.class));
    }

    @Test
    void managerCanAssignTaskToProjectOwner() {
        Task task = task(42L, TaskStatus.TODO);
        ProjectMember manager = actor(task, 7L, ProjectRole.MANAGER);
        ProjectMember owner = actor(task, 9L, ProjectRole.OWNER);
        stubLockedTaskAndActor(task, manager);
        when(memberRepository.findByProjectIdAndUserUsername(12L, "owner"))
                .thenReturn(Optional.of(owner));
        when(assignmentRepository.saveAndFlush(any(TaskAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.assign(42L, new AssignTaskRequest("owner"));

        ArgumentCaptor<TaskAssignment> assignmentCaptor = ArgumentCaptor.forClass(TaskAssignment.class);
        verify(assignmentRepository).saveAndFlush(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue().getAssignee()).isSameAs(owner);
        assertThat(assignmentCaptor.getValue().getAssignedBy()).isSameAs(manager);
    }

    @Test
    void legacySubmitReviewEndpointCannotBypassEvidenceSubmission() {
        assertThatThrownBy(() -> service.submitReview(42L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Create a Submission with Evidence and submit it through the submission endpoint");

        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void activeAssigneeCanStartAChangesRequestedTask() {
        Task task = task(42L, TaskStatus.CHANGES_REQUESTED);
        ProjectMember member = actor(task, 7L, ProjectRole.MEMBER);
        TaskAssignment assignment = assignment(task, member);
        stubLockedTaskAndActor(task, member);
        when(assignmentRepository.findByTaskIdAndStatus(42L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(assignment));

        service.start(42L);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        verify(taskRepository).save(task);
    }

    @Test
    void requestChangesStoresReasonAndMovesTaskToChangesRequested() {
        Task task = task(42L, TaskStatus.IN_REVIEW);
        ProjectMember manager = actor(task, 7L, ProjectRole.MANAGER);
        ProjectMember assignee = actor(task, 9L, ProjectRole.MEMBER);
        stubLockedTaskAndActor(task, manager);
        when(assignmentRepository.findByTaskIdAndStatus(42L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(assignment(task, assignee)));
        when(reviewRepository.save(any(TaskReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.requestChanges(42L, new RequestChangesRequest("  Add authorization tests  "));

        ArgumentCaptor<TaskReview> reviewCaptor = ArgumentCaptor.forClass(TaskReview.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getDecision()).isEqualTo(ReviewDecision.CHANGES_REQUESTED);
        assertThat(reviewCaptor.getValue().getMessage()).isEqualTo("Add authorization tests");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CHANGES_REQUESTED);
    }

    @Test
    void approveRejectsUnsatisfiedCriteria() {
        Task task = task(42L, TaskStatus.IN_REVIEW);
        ProjectMember owner = actor(task, 7L, ProjectRole.OWNER);
        ProjectMember assignee = actor(task, 9L, ProjectRole.MEMBER);
        stubLockedTaskAndActor(task, owner);
        when(assignmentRepository.findByTaskIdAndStatus(42L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(assignment(task, assignee)));
        when(criterionRepository.findByTaskIdOrderByPositionAsc(42L))
                .thenReturn(List.of(criterion(task, false)));
        when(criterionRepository.existsByTaskIdAndSatisfiedFalse(42L)).thenReturn(true);

        assertThatThrownBy(() -> service.approve(42L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("All acceptance criteria must be satisfied before approval");

        verify(reviewRepository, never()).save(any(TaskReview.class));
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_REVIEW);
    }

    @Test
    void requestChangesResetsAllAcceptanceCriteria() {
        Task task = task(42L, TaskStatus.IN_REVIEW);
        ProjectMember manager = actor(task, 7L, ProjectRole.MANAGER);
        ProjectMember assignee = actor(task, 9L, ProjectRole.MEMBER);
        List<TaskAcceptanceCriterion> criteria = List.of(
                criterion(task, true),
                criterion(task, true)
        );
        stubLockedTaskAndActor(task, manager);
        when(assignmentRepository.findByTaskIdAndStatus(42L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(assignment(task, assignee)));
        when(criterionRepository.findByTaskIdOrderByPositionAsc(42L)).thenReturn(criteria);
        when(reviewRepository.save(any(TaskReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.requestChanges(42L, new RequestChangesRequest("Rework authorization"));

        assertThat(criteria).allMatch(criterion -> !criterion.isSatisfied());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CHANGES_REQUESTED);
    }

    @Test
    void assigneeCannotApproveTheirOwnTask() {
        Task task = task(42L, TaskStatus.IN_REVIEW);
        ProjectMember manager = actor(task, 7L, ProjectRole.MANAGER);
        stubLockedTaskAndActor(task, manager);
        when(assignmentRepository.findByTaskIdAndStatus(42L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(assignment(task, manager)));

        assertThatThrownBy(() -> service.approve(42L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Assignee cannot review their own task");

        verify(reviewRepository, never()).save(any(TaskReview.class));
    }

    @Test
    void assigneeCannotRequestChangesOnTheirOwnTask() {
        Task task = task(42L, TaskStatus.IN_REVIEW);
        ProjectMember owner = actor(task, 7L, ProjectRole.OWNER);
        stubLockedTaskAndActor(task, owner);
        when(assignmentRepository.findByTaskIdAndStatus(42L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(assignment(task, owner)));

        assertThatThrownBy(() -> service.requestChanges(
                42L,
                new RequestChangesRequest("Self-review is not independent")
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Assignee cannot review their own task");

        verify(reviewRepository, never()).save(any(TaskReview.class));
    }

    @Test
    void claimCannotBeReleasedAfterWorkStarts() {
        Task task = task(42L, TaskStatus.IN_PROGRESS);
        ProjectMember member = actor(task, 7L, ProjectRole.MEMBER);
        stubLockedTaskAndActor(task, member);
        when(assignmentRepository.findByTaskIdAndStatus(42L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(assignment(task, member)));

        assertThatThrownBy(() -> service.releaseClaim(42L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only TODO task can be claimed or released");
    }

    @Test
    void criterionContentCannotChangeAfterWorkStarts() {
        Task task = task(42L, TaskStatus.IN_PROGRESS);
        ProjectMember manager = actor(task, 7L, ProjectRole.MANAGER);
        TaskAcceptanceCriterion criterion = criterion(task, false);
        criterion.setId(5L);
        stubLockedTaskAndActor(task, manager);
        when(criterionRepository.findByIdAndTaskId(5L, 42L)).thenReturn(Optional.of(criterion));

        assertThatThrownBy(() -> service.updateCriterion(
                42L,
                5L,
                new UpdateAcceptanceCriterionRequest("Changed scope", null)
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Criteria content and order can only change while task is TODO");

        verify(criterionRepository, never()).save(any(TaskAcceptanceCriterion.class));
    }

    private void stubLockedTaskAndActor(Task task, ProjectMember actor) {
        when(taskRepository.findByIdForUpdate(task.getId())).thenReturn(Optional.of(task));
        when(securityUtils.getCurrentUser()).thenReturn(currentUser);
        when(currentUser.getId()).thenReturn(actor.getUser().getId());
        when(memberRepository.findByProjectIdAndUserId(
                task.getProject().getId(), actor.getUser().getId()
        )).thenReturn(Optional.of(actor));
        if (task.getStatus() == TaskStatus.IN_REVIEW) {
            Submission submission = new Submission();
            submission.setId(88L);
            submission.setTask(task);
            submission.setStatus(SubmissionStatus.SUBMITTED);
            lenient().when(submissionRepository.findFirstByTaskIdAndStatusOrderBySequenceNumberDesc(
                    task.getId(), SubmissionStatus.SUBMITTED
            )).thenReturn(Optional.of(submission));
        }
    }

    private void stubCurrentActor(Project project, ProjectMember actor) {
        when(securityUtils.getCurrentUser()).thenReturn(currentUser);
        when(currentUser.getId()).thenReturn(actor.getUser().getId());
        when(memberRepository.findByProjectIdAndUserId(project.getId(), actor.getUser().getId()))
                .thenReturn(Optional.of(actor));
    }

    private CreateProjectTaskRequest createRequest(String assigneeUsername) {
        return new CreateProjectTaskRequest(
                "  Atomic task  ",
                "  Complete workflow setup  ",
                TaskPriority.HIGH,
                LocalDate.of(2026, 8, 25),
                List.of("  First result  ", "Second result"),
                assigneeUsername
        );
    }

    private Project project(ProjectStatus status) {
        Project project = new Project();
        project.setId(12L);
        project.setStatus(status);
        project.setStartDate(LocalDate.of(2026, 8, 1));
        project.setEndDate(LocalDate.of(2026, 8, 31));
        return project;
    }

    private Task task(Long id, TaskStatus status) {
        Project project = new Project();
        project.setId(12L);
        Task task = new Task();
        task.setId(id);
        task.setProject(project);
        task.setStatus(status);
        return task;
    }

    private ProjectMember actor(Task task, Long userId, ProjectRole role) {
        return actor(task.getProject(), userId, role);
    }

    private ProjectMember actor(Project project, Long userId, ProjectRole role) {
        User user = new User();
        user.setId(userId);
        user.setUsername(role.name().toLowerCase());
        ProjectMember member = new ProjectMember();
        member.setId(userId * 10);
        member.setProject(project);
        member.setUser(user);
        member.setRole(role);
        return member;
    }

    private TaskAssignment assignment(Task task, ProjectMember assignee) {
        TaskAssignment assignment = new TaskAssignment();
        assignment.setTask(task);
        assignment.setAssignee(assignee);
        assignment.setType(AssignmentType.CLAIMED);
        assignment.setStatus(AssignmentStatus.ACTIVE);
        return assignment;
    }

    private TaskAcceptanceCriterion criterion(Task task, boolean satisfied) {
        TaskAcceptanceCriterion criterion = new TaskAcceptanceCriterion();
        criterion.setTask(task);
        criterion.setContent("Acceptance criterion");
        criterion.setPosition(0);
        criterion.setSatisfied(satisfied);
        return criterion;
    }

}
