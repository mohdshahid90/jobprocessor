package com.jobprocessor.jobprocessor.service;

import com.jobprocessor.jobprocessor.config.JobProcessorProperties;
import com.jobprocessor.jobprocessor.model.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerService {

    private final JobService jobService;
    private final JobProcessorProperties properties;

    @Scheduled(fixedDelayString = "#{@jobProcessorProperties.worker.pollIntervalMs}")
    public void pollAndProcessJobs() {
        try {
            Job job = jobService.leaseJob();
            if (job != null) {
                processJob(job);
            }
        } catch (Exception e) {
            log.error("[traceId:{}] Error in worker polling: {}", getTraceId(), e.getMessage(), e);
        }
    }

    private void processJob(Job job) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);
        MDC.put("jobId", job.getId().toString());

        try {
            log.info("[traceId:{}] Processing job: {}", traceId, job.getId());

            // Simulate job processing - in real scenario, this would execute actual task
            boolean success = executeJob(job);

            if (success) {
                jobService.acknowledgeJob(job.getId(), true, null);
            } else {
                jobService.acknowledgeJob(job.getId(), false, "Job processing failed");
            }
        } catch (Exception e) {
            log.error("[traceId:{}] Exception processing job {}: {}", traceId, job.getId(), e.getMessage(), e);
            jobService.acknowledgeJob(job.getId(), false, "Exception: " + e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    private boolean executeJob(Job job) {
        // Simulate job processing with random success/failure for demonstration
        // In real scenario, this would parse payload and execute actual task
        try {
            // Simulate processing time
            Thread.sleep(100 + (long)(Math.random() * 500));

            // 80% success rate for demonstration
            boolean success = Math.random() > 0.2;

            if (!success && job.getRetryCount() < job.getMaxRetries()) {
                log.warn("[traceId:{}] Job processing failed, will retry: {}",
                        MDC.get("traceId"), job.getId());
            }

            return success;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String getTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
