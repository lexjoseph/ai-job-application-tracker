package com.jobtracker.dto.response;

public record ApplicationStatsResponse(
        long applied, long interview, long offer, long rejected, long total) {}
