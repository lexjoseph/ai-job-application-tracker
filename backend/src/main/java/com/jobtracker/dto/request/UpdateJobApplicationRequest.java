package com.jobtracker.dto.request;

import com.jobtracker.domain.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateJobApplicationRequest(
        @NotBlank @Size(max = 255) String company,
        @NotBlank @Size(max = 255) String roleTitle,
        @NotNull ApplicationStatus status,
        @Size(max = 10_000) String notes,
        @Size(max = 2048) String sourceUrl,
        @NotNull LocalDate appliedOn) {}
