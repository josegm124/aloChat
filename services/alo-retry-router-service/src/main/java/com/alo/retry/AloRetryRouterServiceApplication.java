package com.alo.retry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.alo")
public class AloRetryRouterServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AloRetryRouterServiceApplication.class, args);
    }
}
