package com.hrapp.rh.securite;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessValidity;
    private final Duration refreshValidity;

    public JwtService(@Value("${app.security.jwt.secret}") String secret,
                      @Value("${app.security.jwt.access-token-minutes}") long accessMinutes,
                      @Value("${app.security.jwt.refresh-token-days}") long refreshDays) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret doit contenir au moins 32 caractères");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessValidity = Duration.ofMinutes(accessMinutes);
        this.refreshValidity = Duration.ofDays(refreshDays);
    }

    public String generateAccessToken(UtilisateurConnecte utilisateur) {
        return buildToken(utilisateur, TYPE_ACCESS, accessValidity);
    }

    public String generateRefreshToken(UtilisateurConnecte utilisateur) {
        return buildToken(utilisateur, TYPE_REFRESH, refreshValidity);
    }

    public long getAccessValiditySeconds() {
        return accessValidity.toSeconds();
    }

    /** Lit un jeton : renvoie vide s'il est invalide, falsifié ou expiré. */
    public Optional<Claims> readClaims(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    private String buildToken(UtilisateurConnecte utilisateur, String type, Duration validity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(utilisateur.getUsername())
                .claim(CLAIM_TYPE, type)
                .claim("role", utilisateur.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}