package org.example.backendbraiding.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class BookingPaymentTokenService {

    private static final long TOKEN_TTL_MILLIS = 30 * 60 * 1000;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String createToken(Long appointmentId) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(appointmentId.toString())
                .claim("purpose", "payment")
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + TOKEN_TTL_MILLIS))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isValidForAppointment(String token, Long appointmentId) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            Object purpose = claims.get("purpose");
            return appointmentId.toString().equals(claims.getSubject())
                    && (purpose == null || "payment".equals(purpose));
        } catch (Exception ignored) {
            return false;
        }
    }

}
