package com.jobprocessor.jobprocessor.config;

import com.jobprocessor.jobprocessor.model.JobStatus;
import com.jobprocessor.jobprocessor.repository.JobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCustomizer(JobRepository jobRepository) {
        return registry -> {
            Gauge.builder("job.processor.pending", () ->
                            jobRepository.countByStatus(JobStatus.PENDING))
                    .description("Number of pending jobs")
                    .register(registry);

            Gauge.builder("job.processor.running", () ->
                            jobRepository.countByStatus(JobStatus.RUNNING))
                    .description("Number of running jobs")
                    .register(registry);

            Gauge.builder("job.processor.completed", () ->
                            jobRepository.countByStatus(JobStatus.COMPLETED))
                    .description("Number of completed jobs")
                    .register(registry);

            Gauge.builder("job.processor.failed", () ->
                            jobRepository.countByStatus(JobStatus.FAILED))
                    .description("Number of failed jobs")
                    .register(registry);

            Gauge.builder("job.processor.dlq", () ->
                            jobRepository.countByStatus(JobStatus.DLQ))
                    .description("Number of DLQ jobs")
                    .register(registry);
        };
    }
}


