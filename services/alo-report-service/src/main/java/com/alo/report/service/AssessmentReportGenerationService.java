package com.alo.report.service;

import com.alo.contracts.assessment.ConsolidatedAssessmentResult;
import com.alo.contracts.assessment.ControlFinding;
import com.alo.contracts.assessment.ControlFindingStatus;
import com.alo.contracts.assessment.EvidenceItem;
import com.alo.contracts.assessment.GeneratedAssessmentReport;
import com.alo.contracts.assessment.PdfReportArtifact;
import com.alo.contracts.assessment.PreferredLanguage;
import com.alo.contracts.assessment.ReportSection;
import com.alo.contracts.assessment.WebAssessmentReport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class AssessmentReportGenerationService {

    public GeneratedAssessmentReport generate(ConsolidatedAssessmentResult consolidatedAssessmentResult) {
        List<ReportSection> sections = buildSections(consolidatedAssessmentResult);
        WebAssessmentReport webReport = buildWebReport(consolidatedAssessmentResult, sections);
        PdfReportArtifact pdfReportArtifact = buildPdfReport(consolidatedAssessmentResult, sections);

        return new GeneratedAssessmentReport(
                UUID.randomUUID().toString(),
                consolidatedAssessmentResult.assessmentId(),
                consolidatedAssessmentResult.preferredLanguage(),
                webReport,
                pdfReportArtifact,
                Instant.now()
        );
    }

    private WebAssessmentReport buildWebReport(
            ConsolidatedAssessmentResult result,
            List<ReportSection> sections
    ) {
        String title = isSpanish(result.preferredLanguage())
                ? "Reporte de cumplimiento"
                : "Compliance report";
        String subtitle = isSpanish(result.preferredLanguage())
                ? "Assessment " + result.assessmentId() + " - trust signal " + result.trustSignal()
                : "Assessment " + result.assessmentId() + " - trust signal " + result.trustSignal();

        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h1>").append(escapeHtml(title)).append("</h1>");
        html.append("<p>").append(escapeHtml(subtitle)).append("</p>");
        for (ReportSection section : sections) {
            html.append("<section>");
            html.append("<h2>").append(escapeHtml(section.title())).append("</h2>");
            html.append("<p>").append(escapeHtml(section.summary())).append("</p>");
            html.append("<ul>");
            for (String item : section.items()) {
                html.append("<li>").append(escapeHtml(item)).append("</li>");
            }
            html.append("</ul>");
            html.append("</section>");
        }
        html.append("</body></html>");

        return new WebAssessmentReport(title, subtitle, html.toString(), sections);
    }

    private PdfReportArtifact buildPdfReport(
            ConsolidatedAssessmentResult result,
            List<ReportSection> sections
    ) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(boldFont, 16);
                contentStream.setLeading(16f);
                contentStream.newLineAtOffset(50, 720);
                writeLine(contentStream, reportTitle(result.preferredLanguage()));
                contentStream.setFont(bodyFont, 11);
                writeLine(contentStream, "Assessment ID: " + result.assessmentId());
                writeLine(contentStream, "Trust signal: " + result.trustSignal());
                writeLine(contentStream, "Overall score: " + result.overallScore() + "/100");
                writeLine(contentStream, " ");
                for (ReportSection section : sections) {
                    contentStream.setFont(boldFont, 12);
                    writeLine(contentStream, section.title());
                    contentStream.setFont(bodyFont, 10);
                    writeLine(contentStream, truncate(section.summary(), 110));
                    for (String item : section.items()) {
                        writeLine(contentStream, "- " + truncate(item, 110));
                    }
                    writeLine(contentStream, " ");
                }
                contentStream.endText();
            }

            document.save(outputStream);
            byte[] pdfBytes = outputStream.toByteArray();
            return new PdfReportArtifact(
                    "assessment-" + result.assessmentId() + ".pdf",
                    "application/pdf",
                    pdfBytes.length,
                    Base64.getEncoder().encodeToString(pdfBytes)
            );
        } catch (IOException exception) {
            byte[] fallback = fallbackPdfPlaceholder(result).getBytes(StandardCharsets.UTF_8);
            return new PdfReportArtifact(
                    "assessment-" + result.assessmentId() + "-fallback.txt",
                    "text/plain",
                    fallback.length,
                    Base64.getEncoder().encodeToString(fallback)
            );
        }
    }

    private List<ReportSection> buildSections(ConsolidatedAssessmentResult result) {
        List<ReportSection> sections = new ArrayList<>();
        sections.add(buildExecutiveSection(result));
        sections.add(buildComplianceOverviewSection(result));
        sections.add(buildGapSection(result));
        sections.add(buildEvidenceSection(result));
        return sections;
    }

    private ReportSection buildExecutiveSection(ConsolidatedAssessmentResult result) {
        String title = isSpanish(result.preferredLanguage()) ? "Resumen ejecutivo" : "Executive summary";
        String summary = result.executiveSummary().summary();
        List<String> items = new ArrayList<>();
        items.add("Trust signal: " + result.trustSignal());
        items.add("Overall score: " + result.overallScore() + "/100");
        items.add("Risk level: " + result.riskLevel());
        items.addAll(result.executiveSummary().recommendedActions());
        return new ReportSection(title, summary, items);
    }

    private ReportSection buildComplianceOverviewSection(ConsolidatedAssessmentResult result) {
        String title = isSpanish(result.preferredLanguage()) ? "Estado de cumplimiento" : "Compliance overview";
        String summary = isSpanish(result.preferredLanguage())
                ? "Vista consolidada de hallazgos por estatus."
                : "Consolidated view of findings by status.";
        List<String> items = List.of(
                "Compliant: " + result.compliantCount(),
                "Partial: " + result.partialCount(),
                "Gaps: " + result.gapCount(),
                "Critical gaps: " + result.criticalGapCount()
        );
        return new ReportSection(title, summary, items);
    }

    private ReportSection buildGapSection(ConsolidatedAssessmentResult result) {
        String title = isSpanish(result.preferredLanguage()) ? "Brechas prioritarias" : "Priority gaps";
        String summary = isSpanish(result.preferredLanguage())
                ? "Controles con brecha o cumplimiento parcial que requieren atencion."
                : "Controls with gaps or partial compliance requiring attention.";
        List<String> items = result.findings().stream()
                .filter(finding -> finding.status() == ControlFindingStatus.GAP
                        || finding.status() == ControlFindingStatus.PARTIAL)
                .limit(8)
                .map(this::formatFinding)
                .toList();
        return new ReportSection(title, summary, items);
    }

    private ReportSection buildEvidenceSection(ConsolidatedAssessmentResult result) {
        String title = isSpanish(result.preferredLanguage()) ? "Evidencia estructurada" : "Structured evidence";
        String summary = isSpanish(result.preferredLanguage())
                ? "Fragmentos y referencias detectadas durante el analisis."
                : "Fragments and references detected during the analysis.";
        List<String> items = result.evidenceItems().stream()
                .limit(8)
                .map(this::formatEvidence)
                .toList();
        return new ReportSection(title, summary, items);
    }

    private String formatFinding(ControlFinding finding) {
        return finding.controlId() + " - " + finding.controlTitle() + " - "
                + finding.status() + " - " + finding.severity();
    }

    private String formatEvidence(EvidenceItem evidenceItem) {
        String text = evidenceItem.extractedText() == null ? "" : truncate(evidenceItem.extractedText(), 90);
        return evidenceItem.evidenceId() + " - " + evidenceItem.type() + " - " + text;
    }

    private String reportTitle(PreferredLanguage preferredLanguage) {
        return isSpanish(preferredLanguage) ? "Reporte de cumplimiento" : "Compliance report";
    }

    private boolean isSpanish(PreferredLanguage preferredLanguage) {
        return preferredLanguage == PreferredLanguage.ES;
    }

    private void writeLine(PDPageContentStream contentStream, String value) throws IOException {
        contentStream.showText(sanitizePdfText(value));
        contentStream.newLine();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private String sanitizePdfText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String fallbackPdfPlaceholder(ConsolidatedAssessmentResult result) {
        return reportTitle(result.preferredLanguage()) + System.lineSeparator()
                + "Assessment ID: " + result.assessmentId() + System.lineSeparator()
                + "Trust signal: " + result.trustSignal() + System.lineSeparator()
                + "Overall score: " + result.overallScore() + "/100";
    }
}
