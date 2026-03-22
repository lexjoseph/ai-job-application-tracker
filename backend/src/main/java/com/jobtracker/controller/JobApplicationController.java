package com.jobtracker.controller;

import com.jobtracker.dto.request.CreateJobApplicationRequest;
import com.jobtracker.dto.request.ScrapeJobUrlRequest;
import com.jobtracker.dto.request.UpdateJobApplicationRequest;
import com.jobtracker.dto.response.ApplicationStatsResponse;
import com.jobtracker.dto.response.JobApplicationResponse;
import com.jobtracker.dto.response.JobPostingScrapeResponse;
import com.jobtracker.service.JobApplicationService;
import com.jobtracker.service.JobPostingScrapeService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
@SecurityRequirement(name = "bearerAuth")
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;
    private final JobPostingScrapeService jobPostingScrapeService;

    public JobApplicationController(
            JobApplicationService jobApplicationService, JobPostingScrapeService jobPostingScrapeService) {
        this.jobApplicationService = jobApplicationService;
        this.jobPostingScrapeService = jobPostingScrapeService;
    }

    @GetMapping("/stats")
    public ApplicationStatsResponse stats() {
        return jobApplicationService.stats();
    }

    @GetMapping
    public List<JobApplicationResponse> list() {
        return jobApplicationService.listMine();
    }

    @PostMapping("/scrape-from-url")
    public JobPostingScrapeResponse scrapeFromUrl(@Valid @RequestBody ScrapeJobUrlRequest request) {
        return jobPostingScrapeService.scrape(request.url());
    }

    @GetMapping("/{id}")
    public JobApplicationResponse get(@PathVariable UUID id) {
        return jobApplicationService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobApplicationResponse create(@Valid @RequestBody CreateJobApplicationRequest request) {
        return jobApplicationService.create(request);
    }

    @PutMapping("/{id}")
    public JobApplicationResponse update(
            @PathVariable UUID id, @Valid @RequestBody UpdateJobApplicationRequest request) {
        return jobApplicationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        jobApplicationService.delete(id);
    }
}
