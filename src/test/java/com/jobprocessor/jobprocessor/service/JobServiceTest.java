package com.jobprocessor.jobprocessor.service;

import com.jobprocessor.jobprocessor.config.JobProcessorProperties;
import com.jobprocessor.jobprocessor.dto.JobRequest;
import com.jobprocessor.jobprocessor.dto.JobResponse;
import com.jobprocessor.jobprocessor.model.Job;
import com.jobprocessor.jobprocessor.model.JobStatus;
import com.jobprocessor.jobprocessor.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private JobProcessorProperties properties;

    @InjectMocks
    private JobService jobService;

    private JobProcessorProperties.Worker workerConfig;
    private JobProcessorProperties rateLimitConfig;

    @BeforeEach
    void setUp() {
        workerConfig = new JobProcessorProperties.Worker();
        workerConfig.setMaxRetries(3);
        workerConfig.setLeaseDurationSeconds(30);

        rateLimitConfig = new JobProcessorProperties();
        rateLimitConfig.setWorker(workerConfig);

        when(properties.getWorker()).thenReturn(workerConfig);
    }

    @Test
    void testSubmitJob_Success() {
        // Given
        JobRequest request = new JobRequest();
        request.setPayload("{\"task\": \"test\"}");
        String tenantId = "test-tenant";

        when(rateLimitingService.canSubmitJob(tenantId)).thenReturn(true);
        when(jobRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        Job savedJob = Job.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .payload(request.getPayload())
                .status(JobStatus.PENDING)
                .maxRetries(3)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jobRepository.save(any(Job.class))).thenReturn(savedJob);

        // When
        JobResponse response = jobService.submitJob(request, tenantId);

        // Then
        assertNotNull(response);
        assertEquals(JobStatus.PENDING, response.getStatus());
        assertEquals(tenantId, response.getTenantId());
        verify(jobRepository, times(1)).save(any(Job.class));
    }

    @Test
    void testSubmitJob_WithIdempotencyKey_ReturnsExisting() {
        // Given
        JobRequest request = new JobRequest();
        request.setPayload("{\"task\": \"test\"}");
        request.setIdempotencyKey("unique-key-123");
        String tenantId = "test-tenant";

        when(rateLimitingService.canSubmitJob(tenantId)).thenReturn(true);

        Job existingJob = Job.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .payload(request.getPayload())
                .status(JobStatus.COMPLETED)
                .idempotencyKey("unique-key-123")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jobRepository.findByIdempotencyKey("unique-key-123")).thenReturn(Optional.of(existingJob));

        // When
        JobResponse response = jobService.submitJob(request, tenantId);

        // Then
        assertNotNull(response);
        assertEquals(JobStatus.COMPLETED, response.getStatus());
        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void testSubmitJob_RateLimitExceeded() {
        // Given
        JobRequest request = new JobRequest();
        request.setPayload("{\"task\": \"test\"}");
        String tenantId = "test-tenant";

        when(rateLimitingService.canSubmitJob(tenantId)).thenReturn(false);

        // When/Then
        assertThrows(JobService.RateLimitExceededException.class, () -> {
            jobService.submitJob(request, tenantId);
        });

        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void testGetJobStatus_Success() {
        // Given
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .tenantId("test-tenant")
                .status(JobStatus.RUNNING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        // When
        JobResponse response = jobService.getJobStatus(jobId);

        // Then
        assertNotNull(response);
        assertEquals(jobId, response.getId());
        assertEquals(JobStatus.RUNNING, response.getStatus());
    }

    @Test
    void testGetJobStatus_NotFound() {
        // Given
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(JobService.JobNotFoundException.class, () -> {
            jobService.getJobStatus(jobId);
        });
    }

    @Test
    void testAcknowledgeJob_Success() {
        // Given
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .status(JobStatus.RUNNING)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenReturn(job);

        // When
        jobService.acknowledgeJob(jobId, true, null);

        // Then
        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertNotNull(job.getCompletedAt());
        verify(jobRepository, times(1)).save(job);
    }

    @Test
    void testAcknowledgeJob_Failed_WithRetry() {
        // Given
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .status(JobStatus.RUNNING)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenReturn(job);

        // When
        jobService.acknowledgeJob(jobId, false, "Test error");

        // Then
        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals(1, job.getRetryCount());
        assertEquals("Test error", job.getErrorMessage());
        verify(jobRepository, times(1)).save(job);
    }

    @Test
    void testAcknowledgeJob_Failed_MaxRetries_MovesToDLQ() {
        // Given
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .status(JobStatus.RUNNING)
                .retryCount(2)
                .maxRetries(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenReturn(job);

        // When
        jobService.acknowledgeJob(jobId, false, "Final error");

        // Then
        assertEquals(JobStatus.DLQ, job.getStatus());
        assertEquals(3, job.getRetryCount());
        assertEquals("Final error", job.getErrorMessage());
        verify(jobRepository, times(1)).save(job);
    }
}
