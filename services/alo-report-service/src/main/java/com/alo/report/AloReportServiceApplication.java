package com.alo.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.alo")
public class AloReportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AloReportServiceApplication.class, args);
    }
}
