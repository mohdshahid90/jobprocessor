package com.jobprocessor.jobprocessor.controller;

import com.jobprocessor.jobprocessor.dto.DashboardStats;
import com.jobprocessor.jobprocessor.dto.JobResponse;
import com.jobprocessor.jobprocessor.model.JobStatus;
import com.jobprocessor.jobprocessor.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final JobService jobService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> getStats() {
        long pending = jobService.getJobsByStatus(JobStatus.PENDING).size();
        long running = jobService.getJobsByStatus(JobStatus.RUNNING).size();
        long completed = jobService.getJobsByStatus(JobStatus.COMPLETED).size();
        long failed = jobService.getJobsByStatus(JobStatus.FAILED).size();
        long dlq = jobService.getJobsByStatus(JobStatus.DLQ).size();

        DashboardStats stats = DashboardStats.builder()
                .pendingJobs(pending)
                .runningJobs(running)
                .completedJobs(completed)
                .failedJobs(failed)
                .dlqJobs(dlq)
                .totalJobs(pending + running + completed + failed + dlq)
                .build();

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/jobs")
    public ResponseEntity<Map<String, List<JobResponse>>> getAllJobs() {
        Map<String, List<JobResponse>> jobs = new HashMap<>();
        jobs.put("pending", jobService.getJobsByStatus(JobStatus.PENDING));
        jobs.put("running", jobService.getJobsByStatus(JobStatus.RUNNING));
        jobs.put("completed", jobService.getJobsByStatus(JobStatus.COMPLETED));
        jobs.put("failed", jobService.getJobsByStatus(JobStatus.FAILED));
        jobs.put("dlq", jobService.getJobsByStatus(JobStatus.DLQ));
        return ResponseEntity.ok(jobs);
    }
}


