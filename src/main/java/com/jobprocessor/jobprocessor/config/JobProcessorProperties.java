package com.jobprocessor.jobprocessor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "job-processor")
public class JobProcessorProperties {

    private Worker worker = new Worker();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Worker {
        private long pollIntervalMs = 1000;
        private int leaseDurationSeconds = 30;
        private int maxRetries = 3;
    }

    @Data
    public static class RateLimit {
        private int maxConcurrentJobsPerTenant = 5;
        private int maxJobsPerMinutePerTenant = 10;
        private int windowSizeSeconds = 60;
    }
}
