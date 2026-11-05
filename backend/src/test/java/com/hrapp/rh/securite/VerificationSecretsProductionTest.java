package com.hrapp.rh.securite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationSecretsProductionTest {

    @Test
    @DisplayName("Production : le secret de développement (présent dans Git) est refusé au démarrage")
    void shouldRefuseDevelopmentSecret() {
        String secretDev = "change-me-dev-only-0123456789-abcdefghijklmnopqrstuvwxyz-ABCDEFGH";

        assertThatThrownBy(() -> new VerificationSecretsProduction(secretDev, false).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("Production : un secret trop court est refusé")
    void shouldRefuseShortSecret() {
        assertThatThrownBy(() -> new VerificationSecretsProduction("trop-court", false).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Production : un vrai secret aléatoire est accepté, même avec les comptes de démonstration")
    void shouldAcceptRealSecret() {
        String secret = "q8Zt3vN1xWm7Lp2Rk9Hd5Fs0Yc6Gb4Je8Ua1Oi3Te7Ny5Mw2Qr";

        assertThatCode(() -> new VerificationSecretsProduction(secret, true).afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}
