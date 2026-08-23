package org.example.backendbraiding.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.stream.Stream;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    
    @Value("${cors.allowed-origin-patterns}")
    private String[] allowedOriginPatterns;
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(effectiveAllowedOriginPatterns())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Content-Type")
                .allowCredentials(true)
                .maxAge(3600);
    }

    private String[] effectiveAllowedOriginPatterns() {
        return Stream.concat(
                        Arrays.stream(allowedOriginPatterns),
                        Stream.of("https://ahbraiding.com", "https://www.ahbraiding.com"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toArray(String[]::new);
    }
}
