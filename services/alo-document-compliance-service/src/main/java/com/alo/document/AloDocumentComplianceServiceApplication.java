package com.alo.document;

import com.alo.document.config.SearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.alo")
@EnableConfigurationProperties(SearchProperties.class)
public class AloDocumentComplianceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AloDocumentComplianceServiceApplication.class, args);
    }
}
