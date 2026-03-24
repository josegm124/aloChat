package com.alo.report.api;

import com.alo.contracts.assessment.ConsolidatedAssessmentResult;
import com.alo.contracts.assessment.GeneratedAssessmentReport;
import com.alo.report.service.AssessmentReportGenerationService;
import com.alo.report.service.FinalReportAssemblyService;
import com.alo.report.storage.S3ReportStorageService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final AssessmentReportGenerationService assessmentReportGenerationService;
    private final FinalReportAssemblyService finalReportAssemblyService;
    private final S3ReportStorageService s3ReportStorageService;

    public ReportController(
            AssessmentReportGenerationService assessmentReportGenerationService,
            FinalReportAssemblyService finalReportAssemblyService,
            S3ReportStorageService s3ReportStorageService
    ) {
        this.assessmentReportGenerationService = assessmentReportGenerationService;
        this.finalReportAssemblyService = finalReportAssemblyService;
        this.s3ReportStorageService = s3ReportStorageService;
    }

    @PostMapping("/generate")
    public ResponseEntity<PublishedReportResponse> generate(@Valid @RequestBody GenerateReportRequest request) {
        GeneratedAssessmentReport generatedReport =
                assessmentReportGenerationService.generate(request.consolidatedAssessmentResult());
        return ResponseEntity.ok(s3ReportStorageService.store(generatedReport));
    }

    @PostMapping("/finalize")
    public ResponseEntity<PublishedReportResponse> finalizeReport(@Valid @RequestBody FinalizeReportRequest request) {
        ConsolidatedAssessmentResult consolidatedAssessmentResult = finalReportAssemblyService.assemble(
                request.assessment(),
                request.documentAnalysisResult(),
                request.datasetAnalysisResult()
        );
        GeneratedAssessmentReport generatedReport =
                assessmentReportGenerationService.generate(consolidatedAssessmentResult);
        return ResponseEntity.ok(s3ReportStorageService.store(generatedReport));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<GeneratedAssessmentReport> getReport(@PathVariable String reportId) {
        return ResponseEntity.ok(s3ReportStorageService.loadReport(reportId));
    }

    @GetMapping(value = "/{reportId}/view", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewReport(@PathVariable String reportId) {
        return ResponseEntity.ok(s3ReportStorageService.loadHtml(reportId));
    }

    @GetMapping("/{reportId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String reportId) {
        GeneratedAssessmentReport report = s3ReportStorageService.loadReport(reportId);
        byte[] pdfBytes = Base64.getDecoder().decode(report.pdfReportArtifact().base64Content());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(report.pdfReportArtifact().contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(report.pdfReportArtifact().fileName())
                                .build()
                                .toString()
                )
                .body(pdfBytes);
    }
}
