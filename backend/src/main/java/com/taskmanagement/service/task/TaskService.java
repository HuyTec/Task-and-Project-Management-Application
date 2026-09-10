package com.taskmanagement.service.task;

import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.taskmanagement.dto.Response;
import com.taskmanagement.dto.expense.ExpenseResponse;
import com.taskmanagement.dto.page.PageResponse;
import com.taskmanagement.dto.task.CreateTaskRequest;
import com.taskmanagement.dto.task.TaskDetailResponse;
import com.taskmanagement.dto.task.AcceptanceCriterionResponse;
import com.taskmanagement.dto.task.TaskAssignmentResponse;
import com.taskmanagement.dto.task.TaskReviewResponse;
import com.taskmanagement.dto.task.TaskResponse;
import com.taskmanagement.dto.task.TaskWorkspaceView;
import com.taskmanagement.dto.task.UpdateTaskRequest;
import com.taskmanagement.event.ExpenseCacheEvictEvent;
import com.taskmanagement.event.TaskCacheEvictEvent;
import com.taskmanagement.exception.BadRequestException;
import com.taskmanagement.exception.ForbiddenException;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.mapper.ExpenseMapper;
import com.taskmanagement.mapper.TaskMapper;
import com.taskmanagement.mapper.TaskWorkflowMapper;
import com.taskmanagement.model.AssignmentStatus;
import com.taskmanagement.model.Expense;
import com.taskmanagement.model.Task;
import com.taskmanagement.model.Project;
import com.taskmanagement.model.ProjectMember;
import com.taskmanagement.model.ProjectRole;
import com.taskmanagement.model.TaskStatus;
import com.taskmanagement.model.TaskAssignment;
import com.taskmanagement.model.User;
import com.taskmanagement.repository.ExpenseRepository;
import com.taskmanagement.repository.TaskRepository;
import com.taskmanagement.repository.MemberRepository;
import com.taskmanagement.repository.TaskAcceptanceCriterionRepository;
import com.taskmanagement.repository.TaskAssignmentRepository;
import com.taskmanagement.repository.TaskReviewRepository;
import com.taskmanagement.repository.SubmissionReviewRepository;
import com.taskmanagement.model.SubmissionReviewStatus;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.repository.projection.TaskTotalProjection;
import com.taskmanagement.security.CustomUserDetails;
import com.taskmanagement.service.cache.TaskCacheService;
import com.taskmanagement.repository.ProjectRepository;
import com.taskmanagement.utils.SecurityUtils;
import com.taskmanagement.utils.PageableValidator;
import org.springframework.data.domain.Pageable;
import com.taskmanagement.dto.task.TaskFilter;
import org.springframework.data.domain.Page;
import com.taskmanagement.repository.specification.TaskSpecifications;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "title", "status", "priority", "dueDate", "createdAt", "updatedAt"
    );
    private final TaskCacheService taskCacheService;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ExpenseRepository expenseRepository;
    private final ProjectRepository projectRepository;
    private final MemberRepository memberRepository;
    private final TaskAssignmentRepository assignmentRepository;
    private final TaskAcceptanceCriterionRepository criterionRepository;
    private final TaskReviewRepository reviewRepository;
    private final SubmissionReviewRepository submissionReviewRepository;
    private final TaskMapper taskMapper;
    private final TaskWorkflowMapper workflowMapper;
    private final ExpenseMapper expenseMapper;
    private final SecurityUtils securityUtils;
        private final ApplicationEventPublisher eventPublisher;

    private Task ensureTaskMutable(Long userId, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found!"));
        if (task.getProject() == null) {
            if (!task.getUser().getId().equals(userId)) {
                throw new ResourceNotFoundException("Task not found!");
            }
            return task;
        }
        ProjectMember membership = memberRepository.findByProjectIdAndUserId(
                        task.getProject().getId(), userId
                )
                .orElseThrow(() -> new ResourceNotFoundException("Task not found!"));
        if (membership.getRole() != ProjectRole.OWNER
                && membership.getRole() != ProjectRole.MANAGER) {
            throw new ForbiddenException("Project task management requires OWNER or MANAGER role");
        }
        return task;
    }

    private Task ensureTaskReadable(Long userId, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found!"));
        if (task.getProject() == null) {
            if (!task.getUser().getId().equals(userId)) {
                throw new ResourceNotFoundException("Task not found!");
            }
            return task;
        }
        if (!memberRepository.existsByProjectIdAndUserId(task.getProject().getId(), userId)) {
            throw new ResourceNotFoundException("Task not found!");
        }
        return task;
    }

    private Project ensureProjectAvailable(Long userId, Long projectId) {
        return projectRepository.findAccessibleProject(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found!"));
    }

    private Project ensureProjectManageable(Long userId, Long projectId) {
        Project project = ensureProjectAvailable(userId, projectId);
        ProjectMember membership = memberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found!"));
        if (membership.getRole() != ProjectRole.OWNER
                && membership.getRole() != ProjectRole.MANAGER) {
            throw new ForbiddenException("Project task management requires OWNER or MANAGER role");
        }
        return project;
    }

    @Transactional(readOnly=true)
    public Response<TaskDetailResponse> getTaskById(Long id) {
        CustomUserDetails currentUser = securityUtils.getCurrentUser();
        Task task = ensureTaskReadable(currentUser.getId(), id);

        Optional<TaskDetailResponse> cached = taskCacheService.get(currentUser.getId(), id);
        if(cached.isPresent()){
            return Response.success(cached.get(),"Task data retrieved successfully!");
        }

        List<Expense> expenses = expenseRepository.findByTaskIdAndUserId(task.getId(), currentUser.getId());
        List<ExpenseResponse> expenseResponses = expenses.stream().map(expenseMapper::toExpenseResponse).toList();
        Double total = expenses.stream().mapToDouble(Expense::getAmount).sum();
        TaskDetailResponse response = toTaskDetailResponse(task, expenseResponses, total);

        taskCacheService.put(currentUser.getId(), response);

        return Response.success(response, "Task data retrieved successfully!");
    }

    @Transactional(readOnly = true)
    public Response<PageResponse<TaskResponse>> getAllTasks(TaskFilter filter, Pageable pageable) {
        boolean isAdmin = securityUtils.isAdmin(securityUtils.getAuthentication());
        if(!isAdmin) 
            throw new ForbiddenException("Only admin can view all tasks");

        validateFilter(filter);
        PageableValidator.requireAllowedSorts(pageable, ALLOWED_SORTS);
        Page<Task> tasksPage = taskRepository.findAll(TaskSpecifications.all(filter), pageable);
        List<Long> taskIds = tasksPage.getContent().stream().map(Task::getId).toList();

        if(taskIds.isEmpty()){
            return Response.success(PageResponse.from(tasksPage, List.of()), "Task data retrieved successfully!");
        }
        List<TaskTotalProjection> totals = expenseRepository.sumAmountsByTaskIds(taskIds);

        Map<Long, Double> totalMap = totals.stream()
            .collect(Collectors.toMap(TaskTotalProjection::getTaskId, TaskTotalProjection::getTotal));

        List<TaskResponse> taskResponses = toTaskResponses(tasksPage.getContent(), totalMap, null);
        return Response.success(PageResponse.from(tasksPage, taskResponses), "Task data retrieved successfully!");
    }

    @Transactional(readOnly = true)
    public Response<PageResponse<TaskResponse>> getMyTask(TaskFilter filter, Pageable pageable) {
        return getMyTask(filter, pageable, TaskWorkspaceView.MY_WORK);
    }

    @Transactional(readOnly = true)
    public Response<PageResponse<TaskResponse>> getMyTask(
            TaskFilter filter,
            Pageable pageable,
            TaskWorkspaceView workspace
    ) {
        CustomUserDetails currentUser = securityUtils.getCurrentUser();
        validateFilter(filter);
        PageableValidator.requireAllowedSorts(pageable, ALLOWED_SORTS);

        Page<Task> tasksPage = taskRepository.findAll(
                TaskSpecifications.forWorkspace(currentUser.getId(), workspace, filter),
                pageable
        );
        List<Long> taskIds = tasksPage.getContent().stream().map(Task::getId).toList();

        List<TaskTotalProjection> totals = taskIds.isEmpty()
                ? List.of()
                : expenseRepository.sumAmountsByTaskIdsAndUserId(taskIds, currentUser.getId());
        Map<Long, Double> totalMap = totals.stream()
                                            .collect(Collectors
                                                .toMap(TaskTotalProjection::getTaskId, TaskTotalProjection::getTotal));

        List<TaskResponse> content = toTaskResponses(tasksPage.getContent(), totalMap, currentUser.getId());

        PageResponse<TaskResponse> result = PageResponse.from(tasksPage, content);

        return Response.success(result, "Task data retrieved successfully!");
    }

    @Transactional(readOnly = true)
    public Response<PageResponse<TaskResponse>> getTasksByProject(Long projectId, TaskFilter filter, Pageable pageable) {
        CustomUserDetails currentUser = securityUtils.getCurrentUser();
        ensureProjectAvailable(currentUser.getId(), projectId);
        validateFilter(filter);
        PageableValidator.requireAllowedSorts(pageable, ALLOWED_SORTS);

        TaskFilter projectFilter = new TaskFilter(
                filter.search(),
                filter.status(),
                filter.priority(),
                projectId,
                filter.dueFrom(),
                filter.dueTo()
        );
        Page<Task> tasksPage = taskRepository.findAll(
                TaskSpecifications.all(projectFilter),
                pageable
        );
        List<Long> taskIds = tasksPage.getContent().stream().map(Task::getId).toList();
        if(taskIds.isEmpty()){
            return Response.success(PageResponse.from(tasksPage, List.of()),
             "Task data retrieved successfully!");
        }

        Map<Long, Double> totalMap = expenseRepository
                .sumAmountsByTaskIdsAndUserId(taskIds, currentUser.getId()).stream()
                .collect(Collectors.toMap(
                        TaskTotalProjection::getTaskId,
                        TaskTotalProjection::getTotal
                ));
        List<TaskResponse> responses = toTaskResponses(tasksPage.getContent(), totalMap, currentUser.getId());
        return Response.success(PageResponse.from(tasksPage, responses), "Project tasks retrieved successfully!");
    }

    private List<TaskResponse> toTaskResponses(
            List<Task> tasks,
            Map<Long, Double> totalMap,
            Long currentUserId
    ) {
        if (tasks.isEmpty()) {
            return List.of();
        }

        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        Map<Long, TaskAssignment> assignmentMap = assignmentRepository
                .findByTaskIdInAndStatus(taskIds, AssignmentStatus.ACTIVE).stream()
                .collect(Collectors.toMap(assignment -> assignment.getTask().getId(), assignment -> assignment));

        Map<Long, ProjectRole> roleMap = Map.of();
        if (currentUserId != null) {
            List<Long> projectIds = tasks.stream()
                    .filter(task -> task.getProject() != null)
                    .map(task -> task.getProject().getId())
                    .distinct()
                    .toList();
            if (!projectIds.isEmpty()) {
                roleMap = memberRepository.findByProjectIdInAndUserId(projectIds, currentUserId).stream()
                        .collect(Collectors.toMap(
                                membership -> membership.getProject().getId(),
                                ProjectMember::getRole
                        ));
            }
        }

        Map<Long, ProjectRole> currentRoleMap = roleMap;
        return tasks.stream()
                .map(task -> taskMapper.toTaskResponse(
                        task,
                        totalMap.getOrDefault(task.getId(), 0.0),
                        assignmentMap.get(task.getId()),
                        task.getProject() == null ? null : currentRoleMap.get(task.getProject().getId())
                ))
                .toList();
    }

    private void validateFilter(TaskFilter filter) {
        if (filter.dueFrom() != null && filter.dueTo() != null
                && filter.dueFrom().isAfter(filter.dueTo())) {
            throw new BadRequestException("Due from must be on or before due to");
        }
    }

    public Response<TaskResponse> createTask(CreateTaskRequest request){
        if (request.projectId() != null) {
            throw new BadRequestException(
                    "Project tasks must be created through the project task endpoint"
            );
        }
        CustomUserDetails currentUser = securityUtils.getCurrentUser();


        Task task = taskMapper.toTask(request);
        User user = userRepository.findById(currentUser.getId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found!"));
        task.setUser(user);
        task.setStatus(TaskStatus.TODO);
        taskRepository.save(task);

        TaskResponse response = taskMapper.toTaskResponse(task, 0.0);
        return Response.success(response, "Task created successfully!");
    }

    public Response<TaskDetailResponse> updateTask(Long id, UpdateTaskRequest request) {
        CustomUserDetails currentUser = securityUtils.getCurrentUser();
        Task task = ensureTaskMutable(currentUser.getId(), id);
        Project previousProject = task.getProject();
        if (request.projectId() != null) {
            throw new BadRequestException(
                    "Task project relation cannot change through the generic update endpoint"
            );
        }

        if (request.title() != null) { 
            if (request.title().isBlank()) {
                throw new BadRequestException("Title cannot be blank");
            }
            task.setTitle(request.title());
        }

        if (request.description() != null) {
            task.setDescription(request.description());
        }

        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        
        if (request.status() != null) {
            if (task.getProject() != null) {
                throw new BadRequestException(
                        "Project task status must change through workflow endpoints"
                );
            }
            task.setStatus(request.status());
        }

        if (request.dueDate() != null){
            task.setDueDate(request.dueDate());
        }

        taskRepository.save(task);
        evictTaskForAudiences(id, currentUser.getId(), task.getUser(), previousProject, task.getProject());
        
        List<Expense> expenses = expenseRepository.findByTaskIdAndUserId(task.getId(), currentUser.getId());
        List<ExpenseResponse> expenseResponses = expenses.stream().map(expenseMapper::toExpenseResponse).toList();
        Double total = expenses.stream().mapToDouble(Expense::getAmount).sum();

        TaskDetailResponse response = toTaskDetailResponse(task, expenseResponses, total);

        return Response.success(response, "Task updated successfully!");
    }

    public Response<Void> deleteTaskById(Long id) {
        CustomUserDetails currentUser = securityUtils.getCurrentUser();

        Task task = ensureTaskMutable(currentUser.getId(), id);

        List<Expense> expenses = expenseRepository.findByTaskId(task.getId());
        List<Long> expenseIds = expenses.stream().map(Expense::getId).toList();

        expenses.forEach(ex -> ex.setTask(null));
        expenseRepository.saveAll(expenses);

        taskRepository.delete(task);

        evictTaskForAudiences(id, currentUser.getId(), task.getUser(), task.getProject());
        for (int index = 0; index < expenses.size(); index++) {
            Expense expense = expenses.get(index);
            Long expenseOwnerId = expense.getUser() == null
                    ? currentUser.getId()
                    : expense.getUser().getId();
            eventPublisher.publishEvent(new ExpenseCacheEvictEvent(expenseOwnerId, expenseIds.get(index)));
        }

        return Response.success(null, "Task deleted successfully!");
    }

    public Response<TaskResponse> unlinkFromProject(Long taskId) {
        CustomUserDetails currentUser = securityUtils.getCurrentUser();

        Task task = ensureTaskMutable(currentUser.getId(), taskId);
        requireProjectRelationMutable(taskId);
        Project previousProject = task.getProject();

        task.setProject(null);
        taskRepository.save(task);

        evictTaskForAudiences(taskId, currentUser.getId(), task.getUser(), previousProject);

        TaskResponse response = taskMapper.toTaskResponse(task, 0.0);
        return Response.success(response, "Task unlinked from project successfully!");
    }

    private TaskDetailResponse toTaskDetailResponse(
            Task task,
            List<ExpenseResponse> expenses,
            Double total
    ) {
        List<AcceptanceCriterionResponse> criteria = criterionRepository
                .findByTaskIdOrderByPositionAsc(task.getId()).stream()
                .map(workflowMapper::toCriterionResponse)
                .toList();
        TaskAssignmentResponse activeAssignment = assignmentRepository
                .findByTaskIdAndStatus(task.getId(), AssignmentStatus.ACTIVE)
                .map(workflowMapper::toAssignmentResponse)
                .orElse(null);
        List<TaskReviewResponse> reviews = submissionReviewRepository
                .findBySubmissionTaskIdAndStatusOrderBySubmittedAtAsc(task.getId(), SubmissionReviewStatus.SUBMITTED).stream()
                .map(review -> new TaskReviewResponse(review.getId(), task.getId(), review.getSubmission().getId(),
                        review.getDecision(), review.getMessage(), review.getReviewer().getUser().getUsername(),
                        review.getDecision() == com.taskmanagement.model.ReviewDecision.APPROVED
                                ? TaskStatus.DONE : TaskStatus.CHANGES_REQUESTED, review.getSubmittedAt()))
                .toList();
        return taskMapper.toTaskDetailResponse(
                task,
                expenses,
                total,
                criteria,
                activeAssignment,
                reviews
        );
    }

    private void requireProjectRelationMutable(Long taskId) {
        // Moving a task with workflow history would leave references to members of the old project.
        if (assignmentRepository.existsByTaskId(taskId)
                || criterionRepository.existsByTaskId(taskId)
                || reviewRepository.existsByTaskId(taskId)) {
            throw new BadRequestException(
                    "Task with assignment, criteria or review history cannot change project"
            );
        }
    }

    private void evictTaskForAudiences(
            Long taskId,
            Long actorUserId,
            User taskCreator,
            Project... projects
    ) {
        Set<Long> userIds = new HashSet<>();
        userIds.add(actorUserId);
        if (taskCreator != null && taskCreator.getId() != null) {
            userIds.add(taskCreator.getId());
        }
        for (Project project : projects) {
            if (project != null) {
                memberRepository.findByProjectId(project.getId()).stream()
                        .map(member -> member.getUser().getId())
                        .forEach(userIds::add);
            }
        }
        userIds.forEach(userId ->
                eventPublisher.publishEvent(new TaskCacheEvictEvent(userId, taskId))
        );
    }
}
