package com.taskmanagement.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.taskmanagement.dto.Response;
import com.taskmanagement.dto.review.*;
import com.taskmanagement.service.submission.SubmissionReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/api/submissions/{submissionId}/review") @RequiredArgsConstructor
public class SubmissionReviewController {
    private final SubmissionReviewService service;
    @GetMapping public ResponseEntity<Response<SubmissionReviewScreenResponse>> screen(@PathVariable @Positive Long submissionId) { return ResponseEntity.ok(service.screen(submissionId)); }
    @PutMapping("/draft") public ResponseEntity<Response<SubmissionReviewResponse>> draft(@PathVariable @Positive Long submissionId, @RequestBody @Valid SaveSubmissionReviewRequest request) { return ResponseEntity.ok(service.saveDraft(submissionId, request)); }
    @PostMapping("/decision") public ResponseEntity<Response<SubmissionReviewResponse>> submit(@PathVariable @Positive Long submissionId, @RequestBody @Valid SubmitSubmissionReviewRequest request) { return ResponseEntity.ok(service.submit(submissionId, request)); }
}
