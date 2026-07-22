package org.example.backendbraiding.config;

import org.example.backendbraiding.security.JwtAuthenticationFilter;
import org.example.backendbraiding.security.PublicBookingRateLimitFilter;
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
    private final PublicBookingRateLimitFilter publicBookingRateLimitFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          PublicBookingRateLimitFilter publicBookingRateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.publicBookingRateLimitFilter = publicBookingRateLimitFilter;
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
                .requestMatchers("/Gallery/**", "/gallery/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/availability/slots").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/availability/business-hours").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/time-slots/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/availability/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/availability/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/availability/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/availability/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/time-slots/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/time-slots/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/time-slots/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/time-slots/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/booking").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/booking/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/booking/fix-image-paths").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/booking/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/categories/admin").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/categories/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/subcategories/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories/gallery").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories/gallery-cards").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories/slug/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/categories").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/categories").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/categories").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/subcategories/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/subcategories").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/subcategories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/subcategories").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/subcategories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/subcategories").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/subcategories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/services").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/services/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/services").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/services/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/services").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/services/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/services").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/services/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/appointments").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/appointments/settings").hasRole("ADMIN")
                .requestMatchers("/api/appointments/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/customers").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/customers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/gallery").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/gallery/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/gallery/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/gallery/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/gallery/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/gallery/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/homepage-settings").permitAll()
                .requestMatchers("/api/homepage-settings/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/hero-images").permitAll()
                .requestMatchers("/api/hero-images/**").authenticated()
                .requestMatchers("/api/upload/welcome-video").authenticated()
                .requestMatchers("/api/admin/**").authenticated()
                .anyRequest().authenticated()
            )
            // Register the JWT filter's order first. Spring Security 7 requires a custom
            // filter to have a known order before another filter can be positioned around it.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(publicBookingRateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

}
