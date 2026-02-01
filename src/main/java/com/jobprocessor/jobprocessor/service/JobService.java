package com.jobprocessor.jobprocessor.service;

import com.jobprocessor.jobprocessor.config.JobProcessorProperties;
import com.jobprocessor.jobprocessor.dto.JobRequest;
import com.jobprocessor.jobprocessor.dto.JobResponse;
import com.jobprocessor.jobprocessor.model.Job;
import com.jobprocessor.jobprocessor.model.JobStatus;
import com.jobprocessor.jobprocessor.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    private final RateLimitingService rateLimitingService;
    private final JobProcessorProperties properties;

    @Transactional
    public JobResponse submitJob(JobRequest request, String tenantId) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);

        try {
            log.info("[traceId:{}] Submitting job for tenant: {}", traceId, tenantId);

            // Check rate limits
            if (!rateLimitingService.canSubmitJob(tenantId)) {
                throw new RateLimitExceededException("Rate limit exceeded for tenant: " + tenantId);
            }

            // Check idempotency
            if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
                var existingJob = jobRepository.findByIdempotencyKey(request.getIdempotencyKey());
                if (existingJob.isPresent()) {
                    log.info("[traceId:{}] Job with idempotency key {} already exists: {}",
                            traceId, request.getIdempotencyKey(), existingJob.get().getId());
                    return toJobResponse(existingJob.get());
                }
            }

            // Create new job
            Job job = Job.builder()
                    .tenantId(tenantId)
                    .payload(request.getPayload())
                    .idempotencyKey(request.getIdempotencyKey())
                    .status(JobStatus.PENDING)
                    .maxRetries(properties.getWorker().getMaxRetries())
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            job = jobRepository.save(job);
            log.info("[traceId:{}] Job created successfully: {}", traceId, job.getId());

            return toJobResponse(job);
        } finally {
            MDC.clear();
        }
    }

    public JobResponse getJobStatus(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobId));
        return toJobResponse(job);
    }

    @Transactional
    public Job leaseJob() {
        LocalDateTime expiryTime = LocalDateTime.now()
                .minusSeconds(properties.getWorker().getLeaseDurationSeconds());

        List<Job> availableJobs = jobRepository.findAvailableJobsForProcessing(
                JobStatus.PENDING, expiryTime);

        for (Job job : availableJobs) {
            int updated = jobRepository.leaseJob(
                    job.getId(),
                    JobStatus.PENDING,
                    JobStatus.RUNNING,
                    LocalDateTime.now()
            );

            if (updated > 0) {
                job.setStatus(JobStatus.RUNNING);
                job.setLeasedAt(LocalDateTime.now());
                job.setStartedAt(LocalDateTime.now());
                log.info("[traceId:{}] Job leased: {}", getTraceId(), job.getId());
                return jobRepository.findById(job.getId()).orElse(job);
            }
        }

        return null;
    }

    @Transactional
    public void acknowledgeJob(UUID jobId, boolean success, String errorMessage) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobId));

        String traceId = getTraceId();
        MDC.put("traceId", traceId);

        try {
            if (success) {
                job.setStatus(JobStatus.COMPLETED);
                job.setCompletedAt(LocalDateTime.now());
                log.info("[traceId:{}] Job completed: {}", traceId, jobId);
            } else {
                if (job.canRetry()) {
                    job.setRetryCount(job.getRetryCount() + 1);
                    job.setStatus(JobStatus.PENDING);
                    job.setLeasedAt(null);
                    job.setStartedAt(null);
                    job.setErrorMessage(errorMessage);
                    log.warn("[traceId:{}] Job failed, retrying ({}/{}): {}",
                            traceId, job.getRetryCount(), job.getMaxRetries(), jobId);
                } else {
                    job.setStatus(JobStatus.DLQ);
                    job.setErrorMessage(errorMessage);
                    job.setCompletedAt(LocalDateTime.now());
                    log.error("[traceId:{}] Job moved to DLQ after max retries: {}", traceId, jobId);
                }
            }

            jobRepository.save(job);
        } finally {
            MDC.clear();
        }
    }

    public List<JobResponse> getJobsByStatus(JobStatus status) {
        return jobRepository.findByStatus(status).stream()
                .map(this::toJobResponse)
                .collect(Collectors.toList());
    }

    private JobResponse toJobResponse(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .tenantId(job.getTenantId())
                .status(job.getStatus())
                .payload(job.getPayload())
                .idempotencyKey(job.getIdempotencyKey())
                .retryCount(job.getRetryCount())
                .maxRetries(job.getMaxRetries())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }

    private String getTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public static class JobNotFoundException extends RuntimeException {
        public JobNotFoundException(String message) {
            super(message);
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
