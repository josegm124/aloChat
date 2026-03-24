package com.alo.profile.service;

import com.alo.contracts.assessment.ApplicableFramework;
import com.alo.contracts.assessment.AssessmentEnvelope;
import com.alo.contracts.assessment.RegulatoryProfile;
import com.alo.contracts.assessment.RiskLevel;
import com.alo.contracts.assessment.Sector;
import com.alo.profile.api.ProfileResolutionRequest;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RegulatoryProfileResolverService {
    private final Map<Sector, SectorProfileTemplate> templates;

    public RegulatoryProfileResolverService() {
        this.templates = new EnumMap<>(Sector.class);
        templates.put(Sector.HEALTHCARE, new SectorProfileTemplate(
                List.of(ApplicableFramework.EU_AI_ACT, ApplicableFramework.GDPR),
                List.of(
                        "healthcare-high-risk",
                        "risk-management",
                        "data-governance",
                        "technical-documentation",
                        "transparency-human-oversight",
                        "accuracy-robustness",
                        "cybersecurity"
                ),
                List.of(
                        "organization",
                        "geography",
                        "aiSystemCategory",
                        "useCaseType",
                        "usesSensitiveData",
                        "humanOversight"
                ),
                "Healthcare AI systems are assessed under a high-risk oriented profile with strong emphasis on patient impact, data governance, transparency, and human oversight."
        ));
        templates.put(Sector.HR, new SectorProfileTemplate(
                List.of(ApplicableFramework.EU_AI_ACT, ApplicableFramework.GDPR),
                List.of(
                        "hr-high-risk",
                        "risk-management",
                        "data-governance",
                        "technical-documentation",
                        "transparency-human-oversight",
                        "accuracy-robustness",
                        "cybersecurity"
                ),
                List.of(
                        "organization",
                        "geography",
                        "aiSystemCategory",
                        "useCaseType",
                        "usesPersonalData",
                        "humanOversight"
                ),
                "HR AI systems are assessed under a high-risk oriented profile with strong emphasis on fairness, transparency, oversight, and personal data handling."
        ));
    }

    public RegulatoryProfile resolve(ProfileResolutionRequest request) {
        SectorProfileTemplate template = templates.get(request.sector());
        if (template == null) {
            throw new IllegalArgumentException("Unsupported sector: " + request.sector());
        }

        RiskLevel riskLevel = inferRiskLevel(request);
        String profileId = request.sector().name().toLowerCase()
                + ":"
                + normalize(request.geography())
                + ":"
                + normalize(request.aiSystemCategory());

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("useCaseType", request.useCaseType());
        metadata.put("preferredLanguage", request.preferredLanguage().name());
        metadata.put("usesPersonalData", Boolean.toString(request.usesPersonalData()));
        metadata.put("usesSensitiveData", Boolean.toString(request.usesSensitiveData()));
        metadata.put("humanOversight", Boolean.toString(request.humanOversight()));

        return new RegulatoryProfile(
                profileId,
                request.sector(),
                request.geography(),
                request.aiSystemCategory(),
                riskLevel,
                riskLevel == RiskLevel.HIGH_RISK,
                template.applicableFrameworks(),
                template.applicableControlPacks(),
                template.requiredIntakeFields(),
                template.summary(),
                metadata
        );
    }

    public RegulatoryProfile resolve(AssessmentEnvelope assessment) {
        return resolve(new ProfileResolutionRequest(
                assessment.sector(),
                assessment.geography(),
                assessment.aiSystemCategory(),
                assessment.useCaseType(),
                assessment.preferredLanguage(),
                metadataFlag(assessment, "intake.usesPersonalData"),
                metadataFlag(assessment, "intake.usesSensitiveData"),
                metadataFlag(assessment, "intake.humanOversight")
        ));
    }

    private RiskLevel inferRiskLevel(ProfileResolutionRequest request) {
        return switch (request.sector()) {
            case HEALTHCARE, HR -> RiskLevel.HIGH_RISK;
        };
    }

    private String normalize(String value) {
        return value.trim().toLowerCase().replace(' ', '-');
    }

    private boolean metadataFlag(AssessmentEnvelope assessment, String key) {
        if (assessment.metadata() == null) {
            return false;
        }
        return Boolean.parseBoolean(assessment.metadata().getOrDefault(key, "false"));
    }

    private record SectorProfileTemplate(
            List<ApplicableFramework> applicableFrameworks,
            List<String> applicableControlPacks,
            List<String> requiredIntakeFields,
            String summary
    ) {
    }
}
