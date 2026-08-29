package org.example.backendbraiding.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

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

    private Key key() { return Keys.hmacShaKeyFor(jwtSecret.getBytes()); }

    public record IssuedToken(String value, Instant expiresAt) {}
}
