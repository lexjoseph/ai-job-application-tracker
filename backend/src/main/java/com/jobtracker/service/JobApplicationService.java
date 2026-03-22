package com.jobtracker.service;

import com.jobtracker.domain.ApplicationStatus;
import com.jobtracker.domain.JobApplication;
import com.jobtracker.domain.User;
import com.jobtracker.dto.request.CreateJobApplicationRequest;
import com.jobtracker.dto.request.UpdateJobApplicationRequest;
import com.jobtracker.dto.response.ApplicationStatsResponse;
import com.jobtracker.dto.response.JobApplicationResponse;
import com.jobtracker.exception.ApiException;
import com.jobtracker.repository.JobApplicationRepository;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.util.SecurityUtils;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final UserRepository userRepository;

    public JobApplicationService(
            JobApplicationRepository jobApplicationRepository, UserRepository userRepository) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<JobApplicationResponse> listMine() {
        UUID userId = SecurityUtils.currentUserId();
        return jobApplicationRepository.findByUserIdOrderByAppliedOnDesc(userId).stream()
                .map(JobApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationStatsResponse stats() {
        UUID userId = SecurityUtils.currentUserId();
        long applied = jobApplicationRepository.countByUserIdAndStatus(userId, ApplicationStatus.APPLIED);
        long interview = jobApplicationRepository.countByUserIdAndStatus(userId, ApplicationStatus.INTERVIEW);
        long offer = jobApplicationRepository.countByUserIdAndStatus(userId, ApplicationStatus.OFFER);
        long rejected = jobApplicationRepository.countByUserIdAndStatus(userId, ApplicationStatus.REJECTED);
        long total = jobApplicationRepository.countByUserId(userId);
        return new ApplicationStatsResponse(applied, interview, offer, rejected, total);
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse get(UUID id) {
        UUID userId = SecurityUtils.currentUserId();
        JobApplication app = jobApplicationRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Application not found."));
        return JobApplicationResponse.from(app);
    }

    @Transactional
    public JobApplicationResponse create(CreateJobApplicationRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "User not found."));
        JobApplication entity = JobApplication.builder()
                .user(user)
                .company(request.company().trim())
                .roleTitle(request.roleTitle().trim())
                .status(request.status())
                .notes(blankToNull(request.notes()))
                .sourceUrl(blankToNull(request.sourceUrl()))
                .appliedOn(request.appliedOn())
                .build();
        jobApplicationRepository.save(entity);
        return JobApplicationResponse.from(entity);
    }

    @Transactional
    public JobApplicationResponse update(UUID id, UpdateJobApplicationRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        JobApplication entity = jobApplicationRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Application not found."));
        entity.setCompany(request.company().trim());
        entity.setRoleTitle(request.roleTitle().trim());
        entity.setStatus(request.status());
        entity.setNotes(blankToNull(request.notes()));
        entity.setSourceUrl(blankToNull(request.sourceUrl()));
        entity.setAppliedOn(request.appliedOn());
        jobApplicationRepository.save(entity);
        return JobApplicationResponse.from(entity);
    }

    @Transactional
    public void delete(UUID id) {
        UUID userId = SecurityUtils.currentUserId();
        JobApplication entity = jobApplicationRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Application not found."));
        jobApplicationRepository.delete(entity);
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
