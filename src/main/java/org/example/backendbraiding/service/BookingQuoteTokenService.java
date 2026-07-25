package org.example.backendbraiding.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.Date;

@Service
public class BookingQuoteTokenService {
    public static final long TOKEN_TTL_SECONDS = 20 * 60;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private Key signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public SignedQuote create(long serviceId, Long lengthOptionId, String foundation,
                              long priceCents, long depositCents, long serviceVersion) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(TOKEN_TTL_SECONDS);
        String token = Jwts.builder()
                .setSubject(String.valueOf(serviceId))
                .claim("purpose", "booking-quote")
                .claim("lengthOptionId", lengthOptionId)
                .claim("foundation", foundation == null ? "" : foundation)
                .claim("priceCents", priceCents)
                .claim("depositCents", depositCents)
                .claim("serviceVersion", serviceVersion)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
        return new SignedQuote(token, expiresAt);
    }

    public QuoteClaims parse(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(signingKey()).build()
                    .parseClaimsJws(token).getBody();
            if (!"booking-quote".equals(claims.get("purpose", String.class))) {
                throw new IllegalArgumentException("Invalid booking quote");
            }
            Number lengthId = claims.get("lengthOptionId", Number.class);
            String foundation = claims.get("foundation", String.class);
            return new QuoteClaims(
                    Long.parseLong(claims.getSubject()),
                    lengthId == null ? null : lengthId.longValue(),
                    foundation == null || foundation.isBlank() ? null : foundation,
                    claims.get("priceCents", Number.class).longValue(),
                    claims.get("depositCents", Number.class).longValue(),
                    claims.get("serviceVersion", Number.class).longValue()
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid or expired booking quote", exception);
        }
    }

    public record SignedQuote(String token, Instant expiresAt) {}
    public record QuoteClaims(long serviceId, Long lengthOptionId, String foundation,
                              long priceCents, long depositCents, long serviceVersion) {}
}
