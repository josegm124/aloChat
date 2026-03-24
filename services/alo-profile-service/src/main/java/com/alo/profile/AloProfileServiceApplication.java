package com.alo.profile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.alo")
public class AloProfileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AloProfileServiceApplication.class, args);
    }
}
