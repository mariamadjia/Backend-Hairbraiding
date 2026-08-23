package org.example.backendbraiding.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rejects browser-originated cross-site mutations. API clients without browser
 * origin headers remain supported, while Stripe webhooks are signature-protected.
 */
@Component
public class TrustedOriginFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final Pattern[] allowedOrigins;

    public TrustedOriginFilter(@Value("${cors.allowed-origin-patterns}") String[] allowedOriginPatterns) {
        this.allowedOrigins = Arrays.stream(allowedOriginPatterns)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(TrustedOriginFilter::originPattern)
                .toArray(Pattern[]::new);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SAFE_METHODS.contains(request.getMethod())
                || request.getRequestURI().startsWith("/api/webhooks/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if ("cross-site".equalsIgnoreCase(fetchSite)) {
            reject(response);
            return;
        }

        String origin = request.getHeader("Origin");
        if ((origin == null || origin.isBlank()) && request.getHeader("Referer") != null) {
            origin = refererOrigin(request.getHeader("Referer"));
        }
        if (origin != null && !origin.isBlank() && !isAllowed(origin)) {
            reject(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowed(String origin) {
        return Arrays.stream(allowedOrigins).anyMatch(pattern -> pattern.matcher(origin).matches());
    }

    private static Pattern originPattern(String configured) {
        String regex = Pattern.quote(configured).replace("*", "\\E.*\\Q");
        return Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE);
    }

    private static String refererOrigin(String referer) {
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getHost() == null) return "";
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port < 0 ? "" : ":" + port);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Untrusted request origin\"}");
    }
}
