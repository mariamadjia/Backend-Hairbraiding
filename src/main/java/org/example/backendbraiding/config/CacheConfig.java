package org.example.backendbraiding.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleCacheManager;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        
        // Configure caches with different TTLs
        cacheManager.setCaches(Arrays.asList(
            new ConcurrentMapCache("bookingCategory"),
            new ConcurrentMapCache("bookingCategories"),
            new ConcurrentMapCache("publicCategories"),
            new ConcurrentMapCache("allCategories"),
            new ConcurrentMapCache("availableSlots"),
            new ConcurrentMapCache("appointments"),
            new ConcurrentMapCache("customers"),
            new ConcurrentMapCache("homepageSettings")
        ));
        
        return cacheManager;
    }
}
