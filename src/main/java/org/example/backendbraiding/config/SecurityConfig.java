package org.example.backendbraiding.config;

import org.example.backendbraiding.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        System.out.println("========== CUSTOM SECURITY CONFIG LOADED ==========");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/webhooks/**").permitAll()
                .requestMatchers("/api/payments/**").permitAll()
                .requestMatchers("/Gallery/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/categories/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/categories/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/categories/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/subcategories/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/subcategories/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/subcategories/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/subcategories/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/services/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/services/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/services/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/services/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/booking/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/booking/fix-image-paths").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/booking/**").authenticated()
                .requestMatchers("/api/availability/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/time-slots/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/time-slots/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/customers").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/customers/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/appointments").permitAll()
                .requestMatchers("/api/appointments/**").authenticated()
                .requestMatchers("/api/gallery", "/api/gallery/featured", "/api/gallery/category/**", "/api/gallery/subcategory/**", "/api/gallery/tag/**", "/api/gallery/tags").permitAll()
                .requestMatchers("/api/gallery/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/homepage-settings").permitAll()
                .requestMatchers("/api/homepage-settings/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/hero-images").permitAll()
                .requestMatchers("/api/hero-images/**").authenticated()
                .requestMatchers("/api/admin/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

}
