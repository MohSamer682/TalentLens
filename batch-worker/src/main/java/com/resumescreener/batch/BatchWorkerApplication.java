package com.resumescreener.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.resumescreener")
@EntityScan("com.resumescreener.common.entity")
@EnableJpaRepositories("com.resumescreener.common.repository")
@EnableAsync
public class BatchWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchWorkerApplication.class, args);
    }
}
