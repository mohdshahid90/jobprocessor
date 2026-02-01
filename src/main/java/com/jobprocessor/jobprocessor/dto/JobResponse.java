package com.jobprocessor.jobprocessor.dto;

import com.jobprocessor.jobprocessor.model.JobStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class JobResponse {
    private UUID id;
    private String tenantId;
    private JobStatus status;
    private String payload;
    private String idempotencyKey;
    private Integer retryCount;
    private Integer maxRetries;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
