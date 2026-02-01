package com.jobprocessor.jobprocessor.controller;

import com.jobprocessor.jobprocessor.dto.DashboardStats;
import com.jobprocessor.jobprocessor.model.JobStatus;
import com.jobprocessor.jobprocessor.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final JobService jobService;

    @Scheduled(fixedRate = 2000)
    public void broadcastStats() {
        try {
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

            messagingTemplate.convertAndSend("/topic/stats", stats);
        } catch (Exception e) {
            log.error("Error broadcasting stats: {}", e.getMessage(), e);
        }
    }
}


