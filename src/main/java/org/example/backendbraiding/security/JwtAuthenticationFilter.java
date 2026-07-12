package org.example.backendbraiding.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Admin endpoints must always be filtered
        if (path.equals("/api/categories/admin")) {
            return false;
        }
        
        boolean skip = path.startsWith("/api/auth/") ||
               path.startsWith("/Gallery/") ||
               path.startsWith("/gallery/") ||
               path.startsWith("/api/webhooks/") ||
               path.startsWith("/api/payments/") ||
               path.equals("/api/booking") ||
               path.startsWith("/api/booking/") ||
               (path.startsWith("/api/gallery/image/")) ||
               (path.startsWith("/api/gallery") && method.equals("GET")) ||
               (path.startsWith("/api/categories/") && method.equals("GET")) ||
               (path.startsWith("/api/subcategories/") && method.equals("GET")) ||
               (path.startsWith("/api/services/") && method.equals("GET")) ||
               (path.startsWith("/api/time-slots/") && method.equals("GET")) ||
               (path.startsWith("/api/availability/") && method.equals("GET"));
        
        return skip;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = getJwtFromRequest(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            String email = jwtTokenProvider.getEmailFromToken(token);
            String role = jwtTokenProvider.getRoleFromToken(token);

            log.debug("JWT Authentication - Email: {}, Raw Role: {}", email, role);

            // Spring Security's hasRole() automatically adds ROLE_ prefix, so we need to strip it if present
            // If role is "ROLE_ADMIN", strip to "ADMIN" for hasRole() to work correctly
            String authority = role.startsWith("ROLE_") ? role.substring(5) : role;
            String finalAuthority = "ROLE_" + authority;

            log.debug("JWT Authentication - Parsed Authority: {}, Final Authority: {}", authority, finalAuthority);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    email, null, Collections.singletonList(new SimpleGrantedAuthority(finalAuthority)));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("JWT Authentication - Successfully authenticated user: {} with authorities: {}",
                email, authentication.getAuthorities());
        } else {
            log.debug("JWT Authentication - No valid token found for request: {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
    
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
