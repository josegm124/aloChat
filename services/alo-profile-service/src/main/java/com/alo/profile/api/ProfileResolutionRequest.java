package com.alo.profile.api;

import com.alo.contracts.assessment.PreferredLanguage;
import com.alo.contracts.assessment.Sector;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProfileResolutionRequest(
        @NotNull Sector sector,
        @NotBlank String geography,
        @NotBlank String aiSystemCategory,
        @NotBlank String useCaseType,
        @NotNull PreferredLanguage preferredLanguage,
        boolean usesPersonalData,
        boolean usesSensitiveData,
        boolean humanOversight
) {
}
