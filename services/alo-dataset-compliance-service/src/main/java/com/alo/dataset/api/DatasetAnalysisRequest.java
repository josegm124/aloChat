package com.alo.dataset.api;

import com.alo.contracts.assessment.PreferredLanguage;
import com.alo.contracts.assessment.Sector;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record DatasetAnalysisRequest(
        @NotBlank String assessmentId,
        @NotBlank String artifactId,
        @NotNull PreferredLanguage preferredLanguage,
        @NotNull Sector sector,
        @NotBlank String regulatoryProfileId,
        @NotNull MultipartFile file
) {
}
