package com.taskmanagement.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.taskmanagement.dto.expense.ExpenseResponse;
import com.taskmanagement.dto.task.CreateTaskRequest;
import com.taskmanagement.dto.task.AcceptanceCriterionResponse;
import com.taskmanagement.dto.task.TaskAssignmentResponse;
import com.taskmanagement.dto.task.TaskDetailResponse;
import com.taskmanagement.dto.task.TaskReviewResponse;
import com.taskmanagement.dto.task.TaskResponse;
import com.taskmanagement.model.Task;
import com.taskmanagement.model.TaskAssignment;
import com.taskmanagement.model.ProjectRole;

@Mapper(componentModel = "spring")
public interface TaskMapper {
    @Mapping(target = "total", source = "total")
    @Mapping(target = "id", source = "task.id")
    @Mapping(target = "status", source = "task.status")
    @Mapping(target = "projectId", source = "task.project.id")
    @Mapping(target = "assigneeUsername", source = "activeAssignment.assignee.user.username")
    @Mapping(target = "assigneeDisplayName", source = "activeAssignment.assignee.user.displayName")
    @Mapping(target = "assignmentType", source = "activeAssignment.type")
    @Mapping(target = "currentUserRole", source = "currentUserRole")
    public TaskResponse toTaskResponse(
            Task task,
            Double total,
            TaskAssignment activeAssignment,
            ProjectRole currentUserRole
    );

    default TaskResponse toTaskResponse(Task task, Double total) {
        return toTaskResponse(task, total, null, null);
    }
    

    @Mapping(target = "userId", source = "task.user.id")
    @Mapping(target = "projectId", source = "task.project.id")
    @Mapping(target = "id", source = "task.id")
    @Mapping(target = "status", source = "task.status")
    @Mapping(target = "acceptanceCriteria", source = "acceptanceCriteria")
    @Mapping(target = "activeAssignment", source = "activeAssignment")
    @Mapping(target = "reviewerUsername", source = "task.reviewer.user.username")
    @Mapping(target = "reviews", source = "reviews")
    @Mapping(target = "expenses", source = "expenses")
    @Mapping(target = "total", source = "total")
    public TaskDetailResponse toTaskDetailResponse(
            Task task,
            List<ExpenseResponse> expenses,
            Double total,
            List<AcceptanceCriterionResponse> acceptanceCriteria,
            TaskAssignmentResponse activeAssignment,
            List<TaskReviewResponse> reviews
    );

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "acceptanceCriteria", ignore = true)
    public Task toTask(CreateTaskRequest request);
}

