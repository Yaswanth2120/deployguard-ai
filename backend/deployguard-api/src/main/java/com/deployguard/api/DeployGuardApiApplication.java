package com.deployguard.api;

import com.deployguard.api.auth.AuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
public class DeployGuardApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeployGuardApiApplication.class, args);
    }
}
