package com.alo.dataset.api;

import com.alo.contracts.assessment.DatasetAnalysisResult;
import com.alo.dataset.service.DatasetComplianceAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dataset-compliance")
public class DatasetComplianceController {
    private final DatasetComplianceAnalysisService datasetComplianceAnalysisService;

    public DatasetComplianceController(DatasetComplianceAnalysisService datasetComplianceAnalysisService) {
        this.datasetComplianceAnalysisService = datasetComplianceAnalysisService;
    }

    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DatasetAnalysisResult analyze(@Valid @ModelAttribute DatasetAnalysisRequest request) {
        return datasetComplianceAnalysisService.analyze(request);
    }
}
