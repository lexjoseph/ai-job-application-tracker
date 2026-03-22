package com.jobtracker.repository;

import com.jobtracker.domain.ApplicationStatus;
import com.jobtracker.domain.JobApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    List<JobApplication> findByUserIdOrderByAppliedOnDesc(UUID userId);

    Optional<JobApplication> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndStatus(UUID userId, ApplicationStatus status);

    @Query("SELECT COUNT(j) FROM JobApplication j WHERE j.user.id = :userId")
    long countByUserId(@Param("userId") UUID userId);
}
