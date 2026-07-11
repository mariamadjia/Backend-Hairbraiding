package org.example.backendbraiding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class BackendBraidingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendBraidingApplication.class, args);
    }

}
