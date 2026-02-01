package com.jobprocessor.jobprocessor.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStats {
    private long pendingJobs;
    private long runningJobs;
    private long completedJobs;
    private long failedJobs;
    private long dlqJobs;
    private long totalJobs;
}
