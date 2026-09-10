package com.taskmanagement.dto.task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record AssignReviewerRequest(@NotBlank @Size(max = 255) String username) { }
