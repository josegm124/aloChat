package com.alo.profile.api;

import com.alo.contracts.assessment.RegulatoryProfile;
import com.alo.profile.service.RegulatoryProfileResolverService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileResolutionController {
    private final RegulatoryProfileResolverService regulatoryProfileResolverService;

    public ProfileResolutionController(RegulatoryProfileResolverService regulatoryProfileResolverService) {
        this.regulatoryProfileResolverService = regulatoryProfileResolverService;
    }

    @PostMapping("/resolve")
    public RegulatoryProfile resolveProfile(@Valid @RequestBody ProfileResolutionRequest request) {
        return regulatoryProfileResolverService.resolve(request);
    }
}
