package com.alo.orchestrator.api;

import com.alo.contracts.assessment.ConsolidatedAssessmentResult;
import com.alo.orchestrator.service.AssessmentConsolidationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assessment-orchestrator")
public class AssessmentOrchestratorController {

    private final AssessmentConsolidationService assessmentConsolidationService;

    public AssessmentOrchestratorController(AssessmentConsolidationService assessmentConsolidationService) {
        this.assessmentConsolidationService = assessmentConsolidationService;
    }

    @PostMapping("/consolidate")
    public ResponseEntity<ConsolidatedAssessmentResult> consolidate(
            @Valid @RequestBody AssessmentConsolidationRequest request
    ) {
        return ResponseEntity.ok(assessmentConsolidationService.consolidate(request));
    }
}
