package org.example.backendbraiding.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class OwnerDepositTokenService {
    @Value("${jwt.secret}") private String jwtSecret;
    @Value("${owner-booking.deposit-link-ttl:PT24H}") private Duration ttl;

    public IssuedToken issue(Long appointmentId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .setSubject(appointmentId.toString())
                .claim("purpose", "owner-deposit")
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public boolean isValid(String token, Long appointmentId) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key()).build()
                    .parseClaimsJws(token).getBody();
            return appointmentId.toString().equals(claims.getSubject())
                    && "owner-deposit".equals(claims.get("purpose"));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public boolean isValid(String token, Long appointmentId, String expectedHash) {
        if (!isValid(token, appointmentId)) return false;
        // Appointments created before token hashes were introduced remain valid
        // until their original token expires or the owner resends the link.
        if (expectedHash == null || expectedHash.isBlank()) return true;
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                hash(token).getBytes(StandardCharsets.US_ASCII));
    }

    public String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private Key key() { return Keys.hmacShaKeyFor(jwtSecret.getBytes()); }

    public record IssuedToken(String value, Instant expiresAt) {}
}
