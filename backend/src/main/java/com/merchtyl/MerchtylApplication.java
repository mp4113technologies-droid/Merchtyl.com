package com.merchtyl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@EntityScan(basePackages = "com.merchtyl")
@EnableJpaRepositories(basePackages = "com.merchtyl")
public class MerchtylApplication {
    public static void main(String[] args) {
        SpringApplication.run(MerchtylApplication.class, args);



        System.out.println("******Merchtyl Started Successfully*****");
    }
}
