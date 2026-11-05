package com.hrapp.rh.securite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteurConnexionsTest {

    private static final class Horloge extends Clock {
        private Instant maintenant = Instant.parse("2026-11-05T10:00:00Z");
        void avancer(Duration d) { maintenant = maintenant.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return maintenant; }
    }

    private final Horloge horloge = new Horloge();
    private final LimiteurConnexions limiteur = new LimiteurConnexions(3, 15, horloge);

    @Test
    @DisplayName("Sous le seuil : jamais bloqué")
    void shouldNotBlock_belowThreshold() {
        limiteur.recordFailure("rh");
        limiteur.recordFailure("rh");

        assertThat(limiteur.blockedFor("rh")).isEmpty();
    }

    @Test
    @DisplayName("Au seuil : bloqué jusqu'à ce que le plus ancien échec sorte de la fenêtre")
    void shouldBlock_untilOldestFailureExpires() {
        limiteur.recordFailure("rh");
        horloge.avancer(Duration.ofMinutes(5));
        limiteur.recordFailure("rh");
        limiteur.recordFailure("rh");

        assertThat(limiteur.blockedFor("rh")).contains(Duration.ofMinutes(10));     // 15 min après le 1er échec
        horloge.avancer(Duration.ofMinutes(10));
        assertThat(limiteur.blockedFor("rh")).isEmpty();                            // le 1er échec est expiré
    }

    @Test
    @DisplayName("Le compte est insensible à la casse et aux espaces ; les autres comptes ne sont pas touchés")
    void shouldTrackPerAccount_caseInsensitive() {
        limiteur.recordFailure("RH ");
        limiteur.recordFailure("rh");
        limiteur.recordFailure("Rh");

        assertThat(limiteur.blockedFor("rh")).isPresent();
        assertThat(limiteur.blockedFor("employe")).isEmpty();
    }

    @Test
    @DisplayName("Une connexion réussie remet le compteur à zéro")
    void success_shouldResetCounter() {
        limiteur.recordFailure("rh");
        limiteur.recordFailure("rh");
        limiteur.recordSuccess("rh");
        limiteur.recordFailure("rh");
        limiteur.recordFailure("rh");

        assertThat(limiteur.blockedFor("rh")).isEmpty();
    }

    @Test
    @DisplayName("Purge : les compteurs expirés sont oubliés (mémoire bornée)")
    void purge_shouldForgetExpiredCounters() {
        limiteur.recordFailure("rh");
        limiteur.recordFailure("employe");
        horloge.avancer(Duration.ofMinutes(16));

        limiteur.purgeExpired();

        assertThat(limiteur.trackedKeys()).isZero();
    }
}
