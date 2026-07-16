package org.example.backendbraiding.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "bookingCategory",
            "bookingCategories",
            "publicCategories",
            "allCategories",
            "galleryCards",
            "availableSlots",
            "appointments",
            "customers",
            "homepageSettings"
        );
        // Expire entries 10 minutes after write; cap each cache at 500 entries
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(500)
        );
        return cacheManager;
    }
}
