package org.example.backendbraiding.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Get upload directory from environment variable or use default
        String uploadDir = System.getenv("UPLOAD_DIR") != null 
            ? System.getenv("UPLOAD_DIR") 
            : "public";
        
        // Ensure path ends with /
        if (!uploadDir.endsWith("/")) {
            uploadDir += "/";
        }
        
        // Serve static files from upload directory
        registry.addResourceHandler("/Gallery/**")
                .addResourceLocations("file:" + uploadDir + "Gallery/")
                .addResourceLocations("classpath:/static/Gallery/");
        
        // Serve uploaded files from uploads directory
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "uploads/")
                .addResourceLocations("classpath:/static/uploads/");
    }
}
