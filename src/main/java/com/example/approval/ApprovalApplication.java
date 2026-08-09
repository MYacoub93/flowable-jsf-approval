package com.example.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot entry point.
 * Flowable auto-configures the ProcessEngine from the starter.
 * JoinFaces auto-configures the FacesServlet and JSF environment.
 * MyBatis scans mappers under com.example.approval.mapper.
 */
@SpringBootApplication
public class ApprovalApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApprovalApplication.class, args);
    }
}
