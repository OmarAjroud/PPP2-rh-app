package com.hrapp.rh.securite;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-0123456789-abcdefghijklmnopqrstuvwxyz-ABCDEFGH";

    private final JwtService service = new JwtService(SECRET, 15, 7);

    @Test
    @DisplayName("Jeton d'accès : lisible, de type « access », avec le bon utilisateur")
    void generateAccessToken_shouldBeReadableAndTypedAccess() {
        String token = service.generateAccessToken(user(0));

        Optional<Claims> claims = service.readClaims(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("admin");
        assertThat(service.isAccessToken(claims.get())).isTrue();
        assertThat(service.isRefreshToken(claims.get())).isFalse();
    }

    @Test
    @DisplayName("Jeton de rafraîchissement : de type « refresh », pas utilisable comme jeton d'accès")
    void generateRefreshToken_shouldBeTypedRefresh() {
        Claims claims = service.readClaims(service.generateRefreshToken(user(0))).orElseThrow();

        assertThat(service.isRefreshToken(claims)).isTrue();
        assertThat(service.isAccessToken(claims)).isFalse();
    }

    @Test
    @DisplayName("Version : le jeton correspond au compte tant que le mot de passe n'a pas changé")
    void matchesVersion_shouldReturnTrue_whenVersionIsTheSame() {
        Claims claims = service.readClaims(service.generateAccessToken(user(2))).orElseThrow();

        assertThat(service.matchesVersion(claims, user(2))).isTrue();
    }

    @Test
    @DisplayName("Version : un jeton émis avant un changement de mot de passe n'est plus valable")
    void matchesVersion_shouldReturnFalse_whenPasswordWasChangedSinceIssue() {
        Claims claims = service.readClaims(service.generateAccessToken(user(0))).orElseThrow();

        assertThat(service.matchesVersion(claims, user(1))).isFalse();
    }

    @Test
    @DisplayName("Jeton falsifié : refusé")
    void readClaims_shouldReturnEmpty_whenTokenIsTampered() {
        String token = service.generateAccessToken(user(0));
        int i = token.length() - 2;                         // un caractère de la signature
        char flipped = token.charAt(i) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, i) + flipped + token.substring(i + 1);

        assertThat(service.readClaims(tampered)).isEmpty();
    }

    @Test
    @DisplayName("Jeton expiré : refusé")
    void readClaims_shouldReturnEmpty_whenTokenIsExpired() {
        JwtService expiredAlready = new JwtService(SECRET, -1, 7);   // durée de vie négative

        String token = expiredAlready.generateAccessToken(user(0));

        assertThat(expiredAlready.readClaims(token)).isEmpty();
    }

    private UtilisateurConnecte user(int version) {
        return new UtilisateurConnecte(1L, 1L, 1L, "admin", "hash", Roles.ADMIN, true, version);
    }
}