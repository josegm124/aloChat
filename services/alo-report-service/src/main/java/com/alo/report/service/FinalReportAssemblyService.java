package com.alo.report.service;

import com.alo.contracts.assessment.ApplicableFramework;
import com.alo.contracts.assessment.AssessmentEnvelope;
import com.alo.contracts.assessment.AssessmentExecutiveSummary;
import com.alo.contracts.assessment.AssessmentStatus;
import com.alo.contracts.assessment.ConsolidatedAssessmentResult;
import com.alo.contracts.assessment.ControlFinding;
import com.alo.contracts.assessment.ControlFindingStatus;
import com.alo.contracts.assessment.DatasetAnalysisResult;
import com.alo.contracts.assessment.DocumentAnalysisResult;
import com.alo.contracts.assessment.EvidenceItem;
import com.alo.contracts.assessment.FindingSeverity;
import com.alo.contracts.assessment.PreferredLanguage;
import com.alo.contracts.assessment.RiskLevel;
import com.alo.contracts.assessment.TrustSignal;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class FinalReportAssemblyService {

    public ConsolidatedAssessmentResult assemble(
            AssessmentEnvelope assessment,
            DocumentAnalysisResult documentAnalysisResult,
            DatasetAnalysisResult datasetAnalysisResult
    ) {
        List<ControlFinding> findings = collectFindings(documentAnalysisResult, datasetAnalysisResult);
        List<EvidenceItem> evidenceItems = collectEvidence(documentAnalysisResult, datasetAnalysisResult);
        int compliantCount = countByStatus(findings, ControlFindingStatus.COMPLIANT);
        int partialCount = countByStatus(findings, ControlFindingStatus.PARTIAL);
        int gapCount = countByStatus(findings, ControlFindingStatus.GAP);
        int criticalGapCount = countCriticalGaps(findings);
        int overallScore = calculateOverallScore(findings);
        TrustSignal trustSignal = resolveTrustSignal(findings, overallScore);
        AssessmentExecutiveSummary executiveSummary = buildExecutiveSummary(
                assessment.preferredLanguage(),
                assessment.regulatoryProfileId(),
                findings,
                overallScore,
                trustSignal
        );

        return new ConsolidatedAssessmentResult(
                assessment.assessmentId(),
                assessment.submissionId(),
                assessment.tenantId(),
                assessment.organizationId(),
                assessment.preferredLanguage(),
                assessment.sector(),
                assessment.regulatoryProfileId(),
                AssessmentStatus.COMPLETED,
                RiskLevel.HIGH_RISK,
                trustSignal,
                overallScore,
                findings.size(),
                compliantCount,
                partialCount,
                gapCount,
                criticalGapCount,
                evidenceItems.size(),
                List.of(ApplicableFramework.EU_AI_ACT, ApplicableFramework.GDPR),
                findings,
                evidenceItems,
                executiveSummary,
                Instant.now()
        );
    }

    private List<ControlFinding> collectFindings(
            DocumentAnalysisResult documentAnalysisResult,
            DatasetAnalysisResult datasetAnalysisResult
    ) {
        List<ControlFinding> findings = new ArrayList<>(safeList(documentAnalysisResult.findings()));
        if (datasetAnalysisResult != null) {
            findings.addAll(safeList(datasetAnalysisResult.findings()));
        }
        findings.sort(Comparator
                .comparingInt((ControlFinding finding) -> statusRank(finding.status()))
                .thenComparingInt(finding -> severityRank(finding.severity()))
                .thenComparing(ControlFinding::controlId, Comparator.nullsLast(String::compareTo)));
        return findings;
    }

    private List<EvidenceItem> collectEvidence(
            DocumentAnalysisResult documentAnalysisResult,
            DatasetAnalysisResult datasetAnalysisResult
    ) {
        Map<String, EvidenceItem> byId = new LinkedHashMap<>();
        for (EvidenceItem evidenceItem : safeList(documentAnalysisResult.evidenceItems())) {
            byId.putIfAbsent(evidenceItem.evidenceId(), evidenceItem);
        }
        return List.copyOf(byId.values());
    }

    private int countByStatus(List<ControlFinding> findings, ControlFindingStatus status) {
        int count = 0;
        for (ControlFinding finding : findings) {
            if (finding.status() == status) {
                count++;
            }
        }
        return count;
    }

    private int countCriticalGaps(List<ControlFinding> findings) {
        int count = 0;
        for (ControlFinding finding : findings) {
            if (finding.status() == ControlFindingStatus.GAP && finding.severity() == FindingSeverity.CRITICAL) {
                count++;
            }
        }
        return count;
    }

    private int calculateOverallScore(List<ControlFinding> findings) {
        int score = 100;
        for (ControlFinding finding : findings) {
            score -= deductionFor(finding);
        }
        return Math.max(0, score);
    }

    private int deductionFor(ControlFinding finding) {
        return switch (finding.status()) {
            case COMPLIANT, NOT_APPLICABLE -> 0;
            case PARTIAL -> switch (finding.severity()) {
                case LOW -> 3;
                case MEDIUM -> 6;
                case HIGH -> 10;
                case CRITICAL -> 15;
            };
            case GAP -> switch (finding.severity()) {
                case LOW -> 5;
                case MEDIUM -> 10;
                case HIGH -> 15;
                case CRITICAL -> 25;
            };
        };
    }

    private TrustSignal resolveTrustSignal(List<ControlFinding> findings, int overallScore) {
        for (ControlFinding finding : findings) {
            if (finding.status() == ControlFindingStatus.GAP && finding.severity() == FindingSeverity.CRITICAL) {
                return TrustSignal.RED;
            }
        }
        if (overallScore < 50) {
            return TrustSignal.RED;
        }
        for (ControlFinding finding : findings) {
            if (finding.status() == ControlFindingStatus.GAP) {
                return TrustSignal.AMBER;
            }
        }
        return overallScore < 80 ? TrustSignal.AMBER : TrustSignal.GREEN;
    }

    private AssessmentExecutiveSummary buildExecutiveSummary(
            PreferredLanguage preferredLanguage,
            String regulatoryProfileId,
            List<ControlFinding> findings,
            int overallScore,
            TrustSignal trustSignal
    ) {
        List<String> topGaps = findings.stream()
                .filter(finding -> finding.status() == ControlFindingStatus.GAP)
                .limit(3)
                .map(finding -> finding.controlTitle() + " [" + finding.severity() + "]")
                .toList();

        LinkedHashSet<String> deduplicatedActions = findings.stream()
                .flatMap(finding -> safeList(finding.recommendedActions()).stream())
                .filter(action -> !action.isBlank())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        List<String> recommendedActions = new ArrayList<>(deduplicatedActions).stream()
                .limit(5)
                .toList();

        if (preferredLanguage == PreferredLanguage.ES) {
            return new AssessmentExecutiveSummary(
                    "Resumen ejecutivo de cumplimiento",
                    "El assessment para el perfil " + regulatoryProfileId
                            + " consolida " + findings.size()
                            + " hallazgos con una senal " + trustSignal
                            + " y un score de " + overallScore + "/100.",
                    topGaps,
                    recommendedActions
            );
        }

        return new AssessmentExecutiveSummary(
                "Executive compliance summary",
                "The assessment for profile " + regulatoryProfileId
                        + " consolidates " + findings.size()
                        + " findings with a " + trustSignal
                        + " trust signal and a score of " + overallScore + "/100.",
                topGaps,
                recommendedActions
        );
    }

    private int statusRank(ControlFindingStatus status) {
        return switch (status) {
            case GAP -> 0;
            case PARTIAL -> 1;
            case COMPLIANT -> 2;
            case NOT_APPLICABLE -> 3;
        };
    }

    private int severityRank(FindingSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
        };
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
