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
import java.util.List;

@Service
public class BookingQuoteTokenService {
    public static final long TOKEN_TTL_SECONDS = 20 * 60;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private Key signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public SignedQuote create(long serviceId, Long lengthOptionId, String foundation,
                              long priceCents, long depositCents, long serviceVersion,
                              List<AddOnClaim> addOns) {
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
                .claim("addOns", encodeAddOns(addOns))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
        return new SignedQuote(token, expiresAt);
    }

    public SignedQuote create(long serviceId, Long lengthOptionId, String foundation,
                              long priceCents, long depositCents, long serviceVersion) {
        return create(serviceId, lengthOptionId, foundation, priceCents, depositCents,
                serviceVersion, List.of());
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
                    claims.get("serviceVersion", Number.class).longValue(),
                    decodeAddOns(claims.get("addOns", String.class))
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid or expired booking quote", exception);
        }
    }

    public record SignedQuote(String token, Instant expiresAt) {}
    public record QuoteClaims(long serviceId, Long lengthOptionId, String foundation,
                              long priceCents, long depositCents, long serviceVersion,
                              List<AddOnClaim> addOns) {}
    public record AddOnClaim(long assignmentId, long addOnId, long addOnVersion,
                             long assignmentVersion, long advertisedPriceCents,
                             long chargedPriceCents) {}

    private String encodeAddOns(List<AddOnClaim> addOns) {
        if (addOns == null || addOns.isEmpty()) return "";
        return addOns.stream().map(item -> String.join(".",
                String.valueOf(item.assignmentId()), String.valueOf(item.addOnId()),
                String.valueOf(item.addOnVersion()), String.valueOf(item.assignmentVersion()),
                String.valueOf(item.advertisedPriceCents()), String.valueOf(item.chargedPriceCents())))
                .reduce((left, right) -> left + "," + right).orElse("");
    }

    private List<AddOnClaim> decodeAddOns(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        try {
            return java.util.Arrays.stream(encoded.split(",")).map(value -> {
                String[] parts = value.split("\\.");
                if (parts.length != 6) throw new IllegalArgumentException("Invalid add-on quote");
                return new AddOnClaim(Long.parseLong(parts[0]), Long.parseLong(parts[1]),
                        Long.parseLong(parts[2]), Long.parseLong(parts[3]),
                        Long.parseLong(parts[4]), Long.parseLong(parts[5]));
            }).toList();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid add-on quote", exception);
        }
    }
}
