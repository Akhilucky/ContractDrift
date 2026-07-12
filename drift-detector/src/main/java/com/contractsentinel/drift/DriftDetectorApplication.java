package com.contractsentinel.drift;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DriftDetectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriftDetectorApplication.class, args);
    }
}
