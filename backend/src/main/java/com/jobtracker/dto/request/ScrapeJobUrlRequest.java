package com.jobtracker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScrapeJobUrlRequest(@NotBlank @Size(max = 2048) String url) {}
