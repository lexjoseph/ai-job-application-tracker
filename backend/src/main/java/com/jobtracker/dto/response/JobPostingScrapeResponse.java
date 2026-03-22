package com.jobtracker.dto.response;

public record JobPostingScrapeResponse(
        String company,
        String roleTitle,
        String sourceUrl,
        String hint) {}
