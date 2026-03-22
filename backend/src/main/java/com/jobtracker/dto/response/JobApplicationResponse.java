package com.jobtracker.dto.response;

import com.jobtracker.domain.ApplicationStatus;
import com.jobtracker.domain.JobApplication;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JobApplicationResponse(
        UUID id,
        String company,
        String roleTitle,
        ApplicationStatus status,
        String notes,
        String sourceUrl,
        LocalDate appliedOn,
        Instant createdAt,
        Instant updatedAt) {

    public static JobApplicationResponse from(JobApplication entity) {
        return new JobApplicationResponse(
                entity.getId(),
                entity.getCompany(),
                entity.getRoleTitle(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getSourceUrl(),
                entity.getAppliedOn(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
