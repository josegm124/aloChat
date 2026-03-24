package com.alo.document.api;

import com.alo.contracts.assessment.DocumentAnalysisResult;
import com.alo.document.service.DocumentComplianceAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/document-compliance")
public class DocumentComplianceController {
    private final DocumentComplianceAnalysisService documentComplianceAnalysisService;

    public DocumentComplianceController(DocumentComplianceAnalysisService documentComplianceAnalysisService) {
        this.documentComplianceAnalysisService = documentComplianceAnalysisService;
    }

    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentAnalysisResult analyze(@Valid @ModelAttribute DocumentAnalysisRequest request) {
        return documentComplianceAnalysisService.analyze(request);
    }
}
