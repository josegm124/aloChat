package com.alo.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.alo")
public class AloAssessmentOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AloAssessmentOrchestratorApplication.class, args);
    }
}
