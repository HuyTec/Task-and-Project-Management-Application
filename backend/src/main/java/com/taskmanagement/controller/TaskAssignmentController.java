package com.taskmanagement.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taskmanagement.dto.Response;
import com.taskmanagement.dto.task.AssignTaskRequest;
import com.taskmanagement.dto.task.AssignReviewerRequest;
import com.taskmanagement.dto.task.TaskAssignmentResponse;
import com.taskmanagement.service.task.TaskWorkflowService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tasks/{taskId}")
@RequiredArgsConstructor
public class TaskAssignmentController {

    private final TaskWorkflowService taskWorkflowService;

    @PutMapping("/reviewer")
    public ResponseEntity<Response<Void>> assignReviewer(@PathVariable @Positive Long taskId,
            @RequestBody @Valid AssignReviewerRequest request) {
        return ResponseEntity.ok(taskWorkflowService.assignReviewer(taskId, request));
    }

    @PostMapping("/claim")
    public ResponseEntity<Response<TaskAssignmentResponse>> claim(
            @PathVariable @Positive Long taskId
    ) {
        return ResponseEntity.ok(taskWorkflowService.claim(taskId));
    }

    @DeleteMapping("/claim")
    public ResponseEntity<Response<Void>> releaseClaim(
            @PathVariable @Positive Long taskId
    ) {
        return ResponseEntity.ok(taskWorkflowService.releaseClaim(taskId));
    }

    @PutMapping("/assignee")
    public ResponseEntity<Response<TaskAssignmentResponse>> assign(
            @PathVariable @Positive Long taskId,
            @RequestBody @Valid AssignTaskRequest request
    ) {
        return ResponseEntity.ok(taskWorkflowService.assign(taskId, request));
    }

    @DeleteMapping("/assignee")
    public ResponseEntity<Response<Void>> clearAssignee(
            @PathVariable @Positive Long taskId
    ) {
        return ResponseEntity.ok(taskWorkflowService.clearAssignee(taskId));
    }
}
