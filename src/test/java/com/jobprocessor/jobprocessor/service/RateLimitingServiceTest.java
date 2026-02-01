package com.jobprocessor.jobprocessor.service;

import com.jobprocessor.jobprocessor.config.JobProcessorProperties;
import com.jobprocessor.jobprocessor.model.JobStatus;
import com.jobprocessor.jobprocessor.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobProcessorProperties properties;

    @InjectMocks
    private RateLimitingService rateLimitingService;

    private JobProcessorProperties.RateLimit rateLimitConfig;

    @BeforeEach
    void setUp() {
        rateLimitConfig = new JobProcessorProperties.RateLimit();
        rateLimitConfig.setMaxConcurrentJobsPerTenant(5);
        rateLimitConfig.setMaxJobsPerMinutePerTenant(10);
        rateLimitConfig.setWindowSizeSeconds(60);

        when(properties.getRateLimit()).thenReturn(rateLimitConfig);
    }

    @Test
    void testCanSubmitJob_WithinLimits() {
        // Given
        String tenantId = "test-tenant";
        when(jobRepository.countByTenantIdAndStatus(tenantId, JobStatus.RUNNING)).thenReturn(2L);

        // When
        boolean result = rateLimitingService.canSubmitJob(tenantId);

        // Then
        assertTrue(result);
    }

    @Test
    void testCanSubmitJob_ExceedsConcurrentLimit() {
        // Given
        String tenantId = "test-tenant";
        when(jobRepository.countByTenantIdAndStatus(tenantId, JobStatus.RUNNING)).thenReturn(5L);

        // When
        boolean result = rateLimitingService.canSubmitJob(tenantId);

        // Then
        assertFalse(result);
    }

    @Test
    void testCanSubmitJob_ExceedsRateLimit() {
        // Given
        String tenantId = "test-tenant";
        when(jobRepository.countByTenantIdAndStatus(tenantId, JobStatus.RUNNING)).thenReturn(0L);

        // Submit 10 jobs rapidly (within rate limit)
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimitingService.canSubmitJob(tenantId));
        }

        // When - 11th job should fail
        boolean result = rateLimitingService.canSubmitJob(tenantId);

        // Then
        assertFalse(result);
    }
}
