package com.alo.intake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.alo")
public class AloIntakeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AloIntakeServiceApplication.class, args);
    }
}
