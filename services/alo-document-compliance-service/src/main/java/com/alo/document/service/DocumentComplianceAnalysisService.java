package com.alo.document.service;

import com.alo.contracts.assessment.ApplicableFramework;
import com.alo.contracts.assessment.ArtifactRef;
import com.alo.contracts.assessment.CompliancePillar;
import com.alo.contracts.assessment.ControlFinding;
import com.alo.contracts.assessment.ControlFindingStatus;
import com.alo.contracts.assessment.DocumentAnalysisResult;
import com.alo.contracts.assessment.EvidenceItem;
import com.alo.contracts.assessment.FindingSeverity;
import com.alo.contracts.events.DocumentAnalysisRequestedEvent;
import com.alo.document.api.DocumentAnalysisRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DocumentComplianceAnalysisService {
    private final Clock clock;
    private final RegulatorySearchClient regulatorySearchClient;

    @Autowired
    public DocumentComplianceAnalysisService(RegulatorySearchClient regulatorySearchClient) {
        this(Clock.systemUTC(), regulatorySearchClient);
    }

    DocumentComplianceAnalysisService(Clock clock, RegulatorySearchClient regulatorySearchClient) {
        this.clock = clock;
        this.regulatorySearchClient = regulatorySearchClient;
    }

    public DocumentAnalysisResult analyze(DocumentAnalysisRequest request) {
        byte[] pdfBytes = toBytes(request);
        ExtractedDocument extractedDocument = extractDocument(pdfBytes);
        Instant now = clock.instant();

        String primaryEvidenceId = UUID.randomUUID().toString();
        EvidenceItem primaryEvidence = new EvidenceItem(
                primaryEvidenceId,
                "PDF_EXTRACT",
                request.file().getOriginalFilename(),
                Integer.toHexString(extractedDocument.text().hashCode()),
                extractedDocument.text(),
                Map.of(
                        "artifactId", request.artifactId(),
                        "contentType", safeContentType(request),
                        "pageCount", Integer.toString(extractedDocument.pageCount())
                )
        );

        List<RegulatorySearchClient.RegulatoryMatch> regulatoryMatches = regulatorySearchClient.search(
                regulatorySearchClient.buildQueryText(
                        request.regulatoryProfileId(),
                        request.sector().name(),
                        trimSearchInput(extractedDocument.text())
                ),
                request.sector().name(),
                5
        );
        List<EvidenceItem> regulatoryEvidence = buildRegulatoryEvidence(regulatoryMatches);
        List<ControlFinding> findings = buildFindings(
                request.assessmentId(),
                primaryEvidenceId,
                extractedDocument.text().isBlank(),
                regulatoryMatches,
                regulatoryEvidence
        );

        return new DocumentAnalysisResult(
                request.assessmentId(),
                request.artifactId(),
                request.preferredLanguage(),
                request.sector(),
                request.regulatoryProfileId(),
                extractedDocument.pageCount(),
                extractedDocument.text().length(),
                preview(extractedDocument.text()),
                combineEvidence(primaryEvidence, regulatoryEvidence),
                findings,
                now
        );
    }

    public DocumentAnalysisResult analyze(DocumentAnalysisRequestedEvent event) {
        Instant now = clock.instant();
        ArtifactRef artifact = event.artifact();

        String primaryEvidenceId = UUID.randomUUID().toString();
        String preview = artifact.checksum() == null
                ? "Pending S3 retrieval for " + artifact.fileName()
                : "Checksum available for " + artifact.fileName() + ": " + artifact.checksum();
        EvidenceItem primaryEvidence = new EvidenceItem(
                primaryEvidenceId,
                "DOCUMENT_METADATA",
                artifact.s3Bucket() + "/" + artifact.s3Key(),
                artifact.checksum(),
                preview,
                Map.of(
                        "artifactId", artifact.artifactId(),
                        "contentType", artifact.contentType(),
                        "fileName", artifact.fileName()
                )
        );

        List<RegulatorySearchClient.RegulatoryMatch> regulatoryMatches = regulatorySearchClient.search(
                regulatorySearchClient.buildQueryText(
                        event.regulatoryProfile().summary(),
                        event.assessment().useCaseType(),
                        artifact.fileName()
                ),
                event.assessment().sector().name(),
                5
        );
        List<EvidenceItem> regulatoryEvidence = buildRegulatoryEvidence(regulatoryMatches);
        List<ControlFinding> findings = buildFindings(
                event.assessment().assessmentId(),
                primaryEvidenceId,
                false,
                regulatoryMatches,
                regulatoryEvidence
        );

        return new DocumentAnalysisResult(
                event.assessment().assessmentId(),
                artifact.artifactId(),
                event.assessment().preferredLanguage(),
                event.assessment().sector(),
                event.regulatoryProfile().regulatoryProfileId(),
                0,
                0,
                preview,
                combineEvidence(primaryEvidence, regulatoryEvidence),
                findings,
                now
        );
    }

    private byte[] toBytes(DocumentAnalysisRequest request) {
        try {
            return request.file().getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read uploaded PDF bytes", exception);
        }
    }

    private ExtractedDocument extractDocument(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            return new ExtractedDocument(document.getNumberOfPages(), textStripper.getText(document).trim());
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to extract text from PDF", exception);
        }
    }

    private List<ControlFinding> buildFindings(
            String assessmentId,
            String primaryEvidenceId,
            boolean emptyDocument,
            List<RegulatorySearchClient.RegulatoryMatch> regulatoryMatches,
            List<EvidenceItem> regulatoryEvidence
    ) {
        if (emptyDocument) {
            return List.of(new ControlFinding(
                    UUID.randomUUID().toString(),
                    assessmentId,
                    ApplicableFramework.EU_AI_ACT,
                    CompliancePillar.TECHNICAL_DOCUMENTATION,
                    "DOC-EMPTY",
                    "No extractable document content",
                    ControlFindingStatus.GAP,
                    FindingSeverity.HIGH,
                    "The uploaded PDF did not expose extractable text, so the regulatory mapping could not be substantiated.",
                    List.of(primaryEvidenceId),
                    List.of("Upload a machine-readable PDF or provide OCR output before running the compliance review again.")
            ));
        }

        if (regulatoryMatches.isEmpty()) {
            return List.of(new ControlFinding(
                    UUID.randomUUID().toString(),
                    assessmentId,
                    ApplicableFramework.EU_AI_ACT,
                    CompliancePillar.TECHNICAL_DOCUMENTATION,
                    "DOC-NO-MATCH",
                    "No regulatory matches found",
                    ControlFindingStatus.GAP,
                    FindingSeverity.HIGH,
                    "The document was readable, but the current regulatory corpus did not return relevant obligations for this content and sector.",
                    List.of(primaryEvidenceId),
                    List.of("Expand the document context or add clearer references to purpose, data governance, oversight, and risk controls.")
            ));
        }

        Map<String, String> evidenceByControl = regulatoryEvidence.stream().collect(Collectors.toMap(
                evidence -> evidence.metadata().get("controlId"),
                EvidenceItem::evidenceId,
                (left, right) -> left
        ));

        return regulatoryMatches.stream()
                .map(match -> new ControlFinding(
                        UUID.randomUUID().toString(),
                        assessmentId,
                        match.framework(),
                        match.pillar(),
                        match.controlId(),
                        match.title(),
                        ControlFindingStatus.PARTIAL,
                        match.score() >= 0.65 ? FindingSeverity.MEDIUM : FindingSeverity.LOW,
                        "The uploaded document matched the regulatory corpus entry '" + match.sourceReference()
                                + "' using " + (match.hybrid() ? "hybrid lexical/vector retrieval" : "lexical retrieval")
                                + " with score " + String.format("%.2f", match.score())
                                + ". This is an evidence-based hint, not a final conformity decision.",
                        List.of(primaryEvidenceId, evidenceByControl.get(match.controlId())),
                        List.of(
                                "Review whether the technical dossier explicitly covers " + match.title().toLowerCase() + ".",
                                "Add documentary evidence linked to " + match.sourceReference() + "."
                        )
                ))
                .toList();
    }

    private List<EvidenceItem> buildRegulatoryEvidence(List<RegulatorySearchClient.RegulatoryMatch> regulatoryMatches) {
        return regulatoryMatches.stream()
                .map(match -> new EvidenceItem(
                        UUID.randomUUID().toString(),
                        "REGULATORY_MATCH",
                        match.sourceUrl(),
                        match.controlId(),
                        match.summary(),
                        Map.of(
                                "controlId", match.controlId(),
                                "framework", match.framework().name(),
                                "pillar", match.pillar().name(),
                                "sourceReference", match.sourceReference(),
                                "score", String.format("%.2f", match.score()),
                                "retrievalMode", match.hybrid() ? "HYBRID" : "LEXICAL"
                        )
                ))
                .toList();
    }

    private List<EvidenceItem> combineEvidence(EvidenceItem primaryEvidence, List<EvidenceItem> regulatoryEvidence) {
        List<EvidenceItem> evidenceItems = new ArrayList<>();
        evidenceItems.add(primaryEvidence);
        evidenceItems.addAll(regulatoryEvidence);
        return List.copyOf(evidenceItems);
    }

    private String preview(String extractedText) {
        if (extractedText.length() <= 500) {
            return extractedText;
        }
        return extractedText.substring(0, 500);
    }

    private String safeContentType(DocumentAnalysisRequest request) {
        return request.file().getContentType() == null ? "application/pdf" : request.file().getContentType();
    }

    private String trimSearchInput(String extractedText) {
        return extractedText.length() <= 1500 ? extractedText : extractedText.substring(0, 1500);
    }

    private record ExtractedDocument(int pageCount, String text) {
    }
}
