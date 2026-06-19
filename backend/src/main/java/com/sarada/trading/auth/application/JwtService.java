package com.sarada.trading.auth.application;

import com.sarada.trading.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(AppProperties props) {
        byte[] raw = Base64.getDecoder().decode(props.security().jwtSecret());
        if (raw.length < 32) {
            throw new IllegalStateException("APP_JWT_SECRET must be at least 32 bytes base64-encoded");
        }
        this.key = new SecretKeySpec(raw, "HmacSHA256");
        this.ttl = Duration.ofMinutes(props.security().jwtTtlMinutes());
    }

    public String issue(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Validates "Bearer <jwt>" and returns an authenticated principal; throws otherwise. */
    public Authentication authenticate(String bearerHeader) {
        if (bearerHeader == null || !bearerHeader.startsWith("Bearer ")) {
            throw new BadCredentialsException("Missing bearer token");
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(bearerHeader.substring(7))
                    .getPayload();
            String role = claims.get("role", String.class);
            return new UsernamePasswordAuthenticationToken(
                    claims.getSubject(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid token");
        }
    }
}
