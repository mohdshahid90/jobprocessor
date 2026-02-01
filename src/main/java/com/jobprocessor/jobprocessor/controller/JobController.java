package com.jobprocessor.jobprocessor.controller;

import com.jobprocessor.jobprocessor.dto.JobRequest;
import com.jobprocessor.jobprocessor.dto.JobResponse;
import com.jobprocessor.jobprocessor.model.JobStatus;
import com.jobprocessor.jobprocessor.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<JobResponse> submitJob(
            @Valid @RequestBody JobRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "default-tenant") String tenantId) {

        log.info("Submitting job for tenant: {}", tenantId);
        JobResponse response = jobService.submitJob(request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJobStatus(@PathVariable UUID jobId) {
        JobResponse response = jobService.getJobStatus(jobId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<JobResponse>> getJobsByStatus(@PathVariable String status) {
        try {
            JobStatus jobStatus = JobStatus.valueOf(status.toUpperCase());
            List<JobResponse> jobs = jobService.getJobsByStatus(jobStatus);
            return ResponseEntity.ok(jobs);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @ExceptionHandler(JobService.RateLimitExceededException.class)
    public ResponseEntity<String> handleRateLimitExceeded(JobService.RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
    }

    @ExceptionHandler(JobService.JobNotFoundException.class)
    public ResponseEntity<String> handleJobNotFound(JobService.JobNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
