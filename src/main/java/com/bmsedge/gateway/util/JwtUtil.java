package com.bmsedge.gateway.util;

import com.bmsedge.gateway.config.JwtConfigProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private SecretKey secretKey;
    private final JwtConfigProperties jwtConfig;

    @Autowired
    public JwtUtil(JwtConfigProperties jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    @PostConstruct
    private void init() {
        String secret = jwtConfig.getSecret();

        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least 32 characters (256 bits). " +
                            "Current size: " + (secret == null ? 0 : secret.length())
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .setSigningKey(secretKey) // or secret string
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public Date extractExpiration(String token) {
        return getClaims(token).getExpiration();
    }

    public boolean isTokenExpired(String token) {
        Date expiration = extractExpiration(token);
        return expiration.before(new Date());
    }

    public boolean validateToken(String token) {
        try {
            if (token == null || token.isBlank()) return false;
            getClaims(token);   // ensures signature validity
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false; // invalid token
        }
    }

    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }
}
