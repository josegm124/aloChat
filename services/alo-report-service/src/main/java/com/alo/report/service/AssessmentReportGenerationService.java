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

    private static final float PAGE_MARGIN = 50f;
    private static final float PAGE_BOTTOM = 50f;

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
        String title = reportTitle(result.preferredLanguage());
        String subtitle = reportSubtitle(result);

        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h1>").append(escapeHtml(title)).append("</h1>");
        html.append("<p>").append(escapeHtml(subtitle)).append("</p>");
        html.append("<p>").append(escapeHtml(assessmentIdDescription(result.preferredLanguage(), result.assessmentId()))).append("</p>");
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
            PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PdfLayoutWriter writer = new PdfLayoutWriter(document);
            try {
                writer.writeParagraph(reportTitle(result.preferredLanguage()), boldFont, 16, 0f, 10f);
                writer.writeParagraph(reportSubtitle(result), bodyFont, 11, 0f, 8f);
                writer.writeParagraph(assessmentIdDescription(result.preferredLanguage(), result.assessmentId()), bodyFont, 10, 0f, 12f);
                for (ReportSection section : sections) {
                    writer.writeParagraph(section.title(), boldFont, 12, 0f, 4f);
                    writer.writeParagraph(section.summary(), bodyFont, 10, 0f, 4f);
                    for (String item : section.items()) {
                        writer.writeBullet(item, bodyFont, 10);
                    }
                    writer.addSpacing(10f);
                }
            } finally {
                writer.close();
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
        items.add(assessmentReferenceLine(result.preferredLanguage(), result.assessmentId()));
        items.add(localizedLabel(result.preferredLanguage(), "Trust signal", "Senal de confianza") + ": " + result.trustSignal());
        items.add(localizedLabel(result.preferredLanguage(), "Overall score", "Puntaje general") + ": " + result.overallScore() + "/100");
        items.add(localizedLabel(result.preferredLanguage(), "Risk level", "Nivel de riesgo") + ": " + result.riskLevel());
        items.addAll(result.executiveSummary().recommendedActions());
        return new ReportSection(title, summary, items);
    }

    private ReportSection buildComplianceOverviewSection(ConsolidatedAssessmentResult result) {
        String title = isSpanish(result.preferredLanguage()) ? "Estado de cumplimiento" : "Compliance overview";
        String summary = isSpanish(result.preferredLanguage())
                ? "Vista consolidada de hallazgos por estatus."
                : "Consolidated view of findings by status.";
        List<String> items = List.of(
                localizedLabel(result.preferredLanguage(), "Compliant", "Cumple") + ": " + result.compliantCount(),
                localizedLabel(result.preferredLanguage(), "Partial", "Parcial") + ": " + result.partialCount(),
                localizedLabel(result.preferredLanguage(), "Gaps", "Brechas") + ": " + result.gapCount(),
                localizedLabel(result.preferredLanguage(), "Critical gaps", "Brechas criticas") + ": " + result.criticalGapCount()
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
                .map(finding -> formatFinding(finding, result.preferredLanguage()))
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
                .map(evidenceItem -> formatEvidence(evidenceItem, result.preferredLanguage()))
                .toList();
        return new ReportSection(title, summary, items);
    }

    private String formatFinding(ControlFinding finding, PreferredLanguage preferredLanguage) {
        return finding.controlId() + " - " + finding.controlTitle()
                + " | " + localizedLabel(preferredLanguage, "Status", "Estatus") + ": " + finding.status()
                + " | " + localizedLabel(preferredLanguage, "Severity", "Severidad") + ": " + finding.severity();
    }

    private String formatEvidence(EvidenceItem evidenceItem, PreferredLanguage preferredLanguage) {
        List<String> parts = new ArrayList<>();
        parts.add(evidenceTypeLabel(evidenceItem.type(), preferredLanguage));

        String reference = evidenceReference(evidenceItem);
        if (!reference.isBlank()) {
            parts.add(localizedLabel(preferredLanguage, "Reference", "Referencia") + ": " + reference);
        }

        String retrievalMode = evidenceItem.metadata() == null ? null : evidenceItem.metadata().get("retrievalMode");
        if (retrievalMode != null && !retrievalMode.isBlank()) {
            parts.add(localizedLabel(preferredLanguage, "Retrieval", "Recuperacion") + ": " + retrievalMode);
        }

        String score = evidenceItem.metadata() == null ? null : evidenceItem.metadata().get("score");
        if (score != null && !score.isBlank()) {
            parts.add(localizedLabel(preferredLanguage, "Score", "Puntaje") + ": " + score);
        }

        String excerpt = sanitizeText(evidenceItem.extractedText());
        if (!excerpt.isBlank()) {
            parts.add(localizedLabel(preferredLanguage, "Excerpt", "Extracto") + ": " + excerpt);
        }

        return String.join(" | ", parts);
    }

    private String evidenceTypeLabel(String type, PreferredLanguage preferredLanguage) {
        if ("PDF_EXTRACT".equals(type)) {
            return localizedLabel(preferredLanguage, "Document excerpt", "Extracto del documento");
        }
        if ("REGULATORY_MATCH".equals(type)) {
            return localizedLabel(preferredLanguage, "Regulatory control match", "Coincidencia de control regulatorio");
        }
        if ("DOCUMENT_METADATA".equals(type)) {
            return localizedLabel(preferredLanguage, "Document metadata", "Metadatos del documento");
        }
        return type == null ? localizedLabel(preferredLanguage, "Evidence item", "Evidencia") : type;
    }

    private String evidenceReference(EvidenceItem evidenceItem) {
        if (evidenceItem.metadata() != null) {
            String sourceReference = evidenceItem.metadata().get("sourceReference");
            if (sourceReference != null && !sourceReference.isBlank()) {
                return sourceReference;
            }
            String fileName = evidenceItem.metadata().get("fileName");
            if (fileName != null && !fileName.isBlank()) {
                return fileName;
            }
            String artifactId = evidenceItem.metadata().get("artifactId");
            if (artifactId != null && !artifactId.isBlank()) {
                return "artifact " + shortValue(artifactId);
            }
        }
        if (evidenceItem.location() != null && !evidenceItem.location().isBlank()) {
            return evidenceItem.location();
        }
        if (evidenceItem.hash() != null && !evidenceItem.hash().isBlank()) {
            return "hash " + shortValue(evidenceItem.hash());
        }
        return "";
    }

    private String reportTitle(PreferredLanguage preferredLanguage) {
        return isSpanish(preferredLanguage) ? "Reporte de cumplimiento" : "Compliance report";
    }

    private String reportSubtitle(ConsolidatedAssessmentResult result) {
        if (isSpanish(result.preferredLanguage())) {
            return "Referencia del assessment " + result.assessmentId()
                    + " | Senal de confianza " + result.trustSignal()
                    + " | Puntaje general " + result.overallScore() + "/100";
        }
        return "Assessment reference " + result.assessmentId()
                + " | Trust signal " + result.trustSignal()
                + " | Overall score " + result.overallScore() + "/100";
    }

    private String assessmentIdDescription(PreferredLanguage preferredLanguage, String assessmentId) {
        if (isSpanish(preferredLanguage)) {
            return "El Assessment ID " + assessmentId
                    + " es la referencia unica para rastrear esta revision, sus hallazgos y los artefactos generados.";
        }
        return "Assessment ID " + assessmentId
                + " is the unique reference used to track this review, its findings, and the generated artifacts.";
    }

    private String assessmentReferenceLine(PreferredLanguage preferredLanguage, String assessmentId) {
        if (isSpanish(preferredLanguage)) {
            return "Assessment ID: " + assessmentId + " (referencia unica del expediente de cumplimiento).";
        }
        return "Assessment ID: " + assessmentId + " (unique reference for this compliance review record).";
    }

    private String localizedLabel(PreferredLanguage preferredLanguage, String englishValue, String spanishValue) {
        return isSpanish(preferredLanguage) ? spanishValue : englishValue;
    }

    private boolean isSpanish(PreferredLanguage preferredLanguage) {
        return preferredLanguage == PreferredLanguage.ES;
    }

    private String shortValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= 12 ? value : value.substring(0, 12);
    }

    private static String sanitizeText(String value) {
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
                + reportSubtitle(result) + System.lineSeparator()
                + assessmentIdDescription(result.preferredLanguage(), result.assessmentId()) + System.lineSeparator();
    }

    private static final class PdfLayoutWriter {

        private final PDDocument document;
        private PDPageContentStream contentStream;
        private float currentY;

        private PdfLayoutWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void writeParagraph(String text, PDType1Font font, float fontSize, float indent, float spaceAfter) throws IOException {
            for (String line : wrap(text, font, fontSize, availableWidth(indent))) {
                writeLine(line, font, fontSize, PAGE_MARGIN + indent);
            }
            addSpacing(spaceAfter);
        }

        private void writeBullet(String text, PDType1Font font, float fontSize) throws IOException {
            List<String> lines = wrap(text, font, fontSize, availableWidth(24f));
            boolean firstLine = true;
            for (String line : lines) {
                String rendered = firstLine ? "- " + line : line;
                float indent = firstLine ? 12f : 24f;
                writeLine(rendered, font, fontSize, PAGE_MARGIN + indent);
                firstLine = false;
            }
            addSpacing(3f);
        }

        private void writeLine(String text, PDType1Font font, float fontSize, float x) throws IOException {
            ensureSpace(lineHeight(fontSize));
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(x, currentY);
            contentStream.showText(sanitizeText(text));
            contentStream.endText();
            currentY -= lineHeight(fontSize);
        }

        private List<String> wrap(String text, PDType1Font font, float fontSize, float width) throws IOException {
            String sanitized = sanitizeText(text);
            if (sanitized.isBlank()) {
                return List.of("");
            }

            List<String> lines = new ArrayList<>();
            StringBuilder currentLine = new StringBuilder();
            for (String word : sanitized.split(" ")) {
                if (word.isBlank()) {
                    continue;
                }
                String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
                if (textWidth(candidate, font, fontSize) <= width) {
                    currentLine.setLength(0);
                    currentLine.append(candidate);
                    continue;
                }
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine.setLength(0);
                }
                if (textWidth(word, font, fontSize) <= width) {
                    currentLine.append(word);
                    continue;
                }
                lines.addAll(splitLongWord(word, font, fontSize, width));
            }
            if (!currentLine.isEmpty()) {
                lines.add(currentLine.toString());
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        private List<String> splitLongWord(String value, PDType1Font font, float fontSize, float width) throws IOException {
            List<String> parts = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (char character : value.toCharArray()) {
                String candidate = current.toString() + character;
                if (textWidth(candidate, font, fontSize) <= width || current.isEmpty()) {
                    current.append(character);
                    continue;
                }
                parts.add(current.toString());
                current.setLength(0);
                current.append(character);
            }
            if (!current.isEmpty()) {
                parts.add(current.toString());
            }
            return parts;
        }

        private float textWidth(String text, PDType1Font font, float fontSize) throws IOException {
            return font.getStringWidth(sanitizeText(text)) / 1000f * fontSize;
        }

        private float availableWidth(float indent) {
            return PDRectangle.LETTER.getWidth() - (PAGE_MARGIN * 2) - indent;
        }

        private float lineHeight(float fontSize) {
            return fontSize + 4f;
        }

        private void addSpacing(float spacing) throws IOException {
            if (spacing <= 0f) {
                return;
            }
            ensureSpace(spacing);
            currentY -= spacing;
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (currentY - requiredHeight >= PAGE_BOTTOM) {
                return;
            }
            newPage();
        }

        private void newPage() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            currentY = PDRectangle.LETTER.getHeight() - PAGE_MARGIN;
        }

        private void close() throws IOException {
            if (contentStream != null) {
                contentStream.close();
                contentStream = null;
            }
        }
    }
}
