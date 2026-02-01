package com.jobprocessor.jobprocessor.repository;

import com.jobprocessor.jobprocessor.model.Job;
import com.jobprocessor.jobprocessor.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    List<Job> findByTenantIdAndStatus(String tenantId, JobStatus status);

    @Query("SELECT COUNT(j) FROM Job j WHERE j.tenantId = :tenantId AND j.status = :status")
    long countByTenantIdAndStatus(@Param("tenantId") String tenantId, @Param("status") JobStatus status);

    @Query("SELECT COUNT(j) FROM Job j WHERE j.status = :status")
    long countByStatus(@Param("status") JobStatus status);

    @Query("SELECT j FROM Job j WHERE j.status = :status AND (j.leasedAt IS NULL OR j.leasedAt < :expiryTime) ORDER BY j.createdAt ASC")
    List<Job> findAvailableJobsForProcessing(@Param("status") JobStatus status, @Param("expiryTime") LocalDateTime expiryTime);

    @Modifying
    @Query("UPDATE Job j SET j.status = :newStatus, j.leasedAt = :leasedAt WHERE j.id = :id AND j.status = :oldStatus")
    int leaseJob(@Param("id") UUID id, @Param("oldStatus") JobStatus oldStatus, @Param("newStatus") JobStatus newStatus, @Param("leasedAt") LocalDateTime leasedAt);

    List<Job> findByStatus(JobStatus status);
}


