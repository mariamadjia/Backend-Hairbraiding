package org.example.backendbraiding.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthCookieService {
    private final String cookieName;
    private final boolean secure;
    private final String sameSite;
    private final String domain;
    private final long rememberDurationSeconds;

    public AuthCookieService(
            @Value("${auth.cookie-name}") String cookieName,
            @Value("${auth.cookie-secure}") boolean secure,
            @Value("${auth.cookie-same-site}") String sameSite,
            @Value("${auth.cookie-domain:}") String domain,
            @Value("${auth.remember-duration-seconds}") long rememberDurationSeconds) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = sameSite;
        this.domain = domain;
        this.rememberDurationSeconds = rememberDurationSeconds;
    }

    public String cookieName() {
        return cookieName;
    }

    public void issue(HttpServletResponse response, String token, boolean rememberDevice) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/");
        if (!domain.isBlank()) builder.domain(domain);
        if (rememberDevice) builder.maxAge(Duration.ofSeconds(rememberDurationSeconds));
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ZERO);
        if (!domain.isBlank()) builder.domain(domain);
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }
}
