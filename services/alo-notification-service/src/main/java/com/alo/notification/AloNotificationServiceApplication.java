package com.alo.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.alo")
public class AloNotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AloNotificationServiceApplication.class, args);
    }
}
