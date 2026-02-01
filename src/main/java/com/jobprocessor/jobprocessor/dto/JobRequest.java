package com.jobprocessor.jobprocessor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JobRequest {
    @NotBlank(message = "Payload is required")
    private String payload;

    private String idempotencyKey;
}
