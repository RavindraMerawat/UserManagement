package com.user.management.security;

import com.user.management.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppProperties properties;

    /**
     * Accepts the secret either as base64 or as a plain passphrase. HS256 needs at
     * least 32 bytes of key material, so a short secret fails fast at startup rather
     * than at the first login.
     */
    private SecretKey signingKey() {
        String secret = properties.getJwt().getSecret();
        byte[] bytes = null;
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length >= 32) {
                bytes = decoded;
            }
        } catch (RuntimeException notBase64) {
            // Not base64, fall through to the raw bytes below.
        }
        if (bytes == null) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256; it is " + bytes.length);
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getJwt().getExpirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(principal.getUsername())
                .issuer(properties.getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claims(Map.of(
                        "uid", principal.getUserId(),
                        "role", principal.getRole().name(),
                        "name", principal.getFullName()))
                .signWith(signingKey())
                .compact();
    }

    public Instant expiryOf(String token) {
        return parse(token).getExpiration().toInstant();
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(properties.getJwt().getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
