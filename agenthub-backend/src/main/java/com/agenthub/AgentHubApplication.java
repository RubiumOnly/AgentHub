package com.agenthub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AgentHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentHubApplication.class, args);
    }
}
