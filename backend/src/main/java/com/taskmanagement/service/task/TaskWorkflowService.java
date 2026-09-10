package com.taskmanagement.service.task;

import com.taskmanagement.dto.Response;
import com.taskmanagement.dto.task.AcceptanceCriterionResponse;
import com.taskmanagement.dto.task.AssignTaskRequest;
import com.taskmanagement.dto.task.AssignReviewerRequest;
import com.taskmanagement.dto.task.CreateAcceptanceCriterionRequest;
import com.taskmanagement.dto.task.CreateProjectTaskRequest;
import com.taskmanagement.dto.task.RequestChangesRequest;
import com.taskmanagement.dto.task.TaskAssignmentResponse;
import com.taskmanagement.dto.task.TaskReviewResponse;
import com.taskmanagement.dto.task.TaskResponse;
import com.taskmanagement.dto.task.TaskWorkflowResponse;
import com.taskmanagement.dto.task.UpdateAcceptanceCriterionRequest;

public interface TaskWorkflowService {

    Response<TaskResponse> createProjectTask(Long projectId, CreateProjectTaskRequest request);

    Response<TaskAssignmentResponse> claim(Long taskId);

    Response<Void> releaseClaim(Long taskId);

    Response<TaskAssignmentResponse> assign(Long taskId, AssignTaskRequest request);

    Response<Void> clearAssignee(Long taskId);

    Response<Void> assignReviewer(Long taskId, AssignReviewerRequest request);

    Response<TaskWorkflowResponse> start(Long taskId);

    Response<AcceptanceCriterionResponse> addCriterion(
            Long taskId,
            CreateAcceptanceCriterionRequest request
    );

    Response<AcceptanceCriterionResponse> updateCriterion(
            Long taskId,
            Long criterionId,
            UpdateAcceptanceCriterionRequest request
    );

    Response<Void> deleteCriterion(Long taskId, Long criterionId);

    Response<TaskWorkflowResponse> submitReview(Long taskId);

    Response<TaskReviewResponse> requestChanges(Long taskId, RequestChangesRequest request);

    Response<TaskReviewResponse> approve(Long taskId);
}
