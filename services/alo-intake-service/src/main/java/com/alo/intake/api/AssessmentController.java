package com.alo.intake.api;

import com.alo.intake.service.AssessmentIntakeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assessments")
public class AssessmentController {
    private final AssessmentIntakeService assessmentIntakeService;

    public AssessmentController(AssessmentIntakeService assessmentIntakeService) {
        this.assessmentIntakeService = assessmentIntakeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CreateAssessmentAcceptedResponse createAssessment(@Valid @RequestBody CreateAssessmentRequest request) {
        return assessmentIntakeService.createAssessment(request);
    }
}
