package com.jobprocessor.jobprocessor.service;

import com.jobprocessor.jobprocessor.config.JobProcessorProperties;
import com.jobprocessor.jobprocessor.model.JobStatus;
import com.jobprocessor.jobprocessor.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitingService {

    private final JobRepository jobRepository;
    private final JobProcessorProperties properties;
    private final Map<String, RateLimitWindow> tenantRateLimitWindows = new ConcurrentHashMap<>();

    public boolean canSubmitJob(String tenantId) {
        // Check concurrent jobs limit
        long runningJobs = jobRepository.countByTenantIdAndStatus(tenantId, JobStatus.RUNNING);
        if (runningJobs >= properties.getRateLimit().getMaxConcurrentJobsPerTenant()) {
            log.warn("[traceId:{}] Tenant {} exceeded concurrent jobs limit: {}/{}",
                    getTraceId(), tenantId, runningJobs, properties.getRateLimit().getMaxConcurrentJobsPerTenant());
            return false;
        }

        // Check rate limit (jobs per minute)
        RateLimitWindow window = tenantRateLimitWindows.computeIfAbsent(tenantId,
                k -> new RateLimitWindow(LocalDateTime.now()));

        window.cleanOldEntries(properties.getRateLimit().getWindowSizeSeconds());

        if (window.getJobCount() >= properties.getRateLimit().getMaxJobsPerMinutePerTenant()) {
            log.warn("[traceId:{}] Tenant {} exceeded rate limit: {}/{} jobs per minute",
                    getTraceId(), tenantId, window.getJobCount(),
                    properties.getRateLimit().getMaxJobsPerMinutePerTenant());
            return false;
        }

        window.recordJob();
        return true;
    }

    private String getTraceId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private static class RateLimitWindow {
        private final LocalDateTime windowStart;
        private final java.util.List<LocalDateTime> jobTimestamps = new java.util.ArrayList<>();

        public RateLimitWindow(LocalDateTime windowStart) {
            this.windowStart = windowStart;
        }

        public void cleanOldEntries(int windowSizeSeconds) {
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(windowSizeSeconds);
            jobTimestamps.removeIf(timestamp -> timestamp.isBefore(cutoff));
        }

        public void recordJob() {
            jobTimestamps.add(LocalDateTime.now());
        }

        public int getJobCount() {
            return jobTimestamps.size();
        }
    }
}


