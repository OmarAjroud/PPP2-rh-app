package com.hrapp.rh.securite;

import com.hrapp.rh.common.exception.TropDeTentativesException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FenetreGlissanteTest {

    private static final class Horloge extends Clock {
        private Instant maintenant = Instant.parse("2026-11-05T10:00:00Z");
        void avancer(Duration d) { maintenant = maintenant.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return maintenant; }
    }

    private final Horloge horloge = new Horloge();

    @Test
    @DisplayName("tryAcquire : accepte N événements, refuse le suivant avec l'attente, puis accepte de nouveau")
    void tryAcquire_shouldEnforceLimitWithinWindow() {
        FenetreGlissante fenetre = new FenetreGlissante(2, Duration.ofMinutes(1), horloge);

        assertThat(fenetre.tryAcquire("k")).isEmpty();
        horloge.avancer(Duration.ofSeconds(20));
        assertThat(fenetre.tryAcquire("k")).isEmpty();
        assertThat(fenetre.tryAcquire("k")).contains(Duration.ofSeconds(40));    // refusé : un refus ne compte pas
        assertThat(fenetre.tryAcquire("autre")).isEmpty();                       // les clés sont indépendantes

        horloge.avancer(Duration.ofSeconds(40));                                  // le 1er événement sort de la fenêtre
        assertThat(fenetre.tryAcquire("k")).isEmpty();
    }

    @Test
    @DisplayName("Chat : 10 questions par minute, la 11e reçoit 429 (TropDeTentativesException) ; autre utilisateur libre")
    void chatLimit_shouldRejectEleventhQuestion() {
        LimitesRequetes limites = new LimitesRequetes(5, 10, 10, 30, horloge);
        for (int i = 0; i < 10; i++) {
            limites.chatQuestion(4L);
        }

        assertThatThrownBy(() -> limites.chatQuestion(4L))
                .isInstanceOf(TropDeTentativesException.class)
                .hasMessageContaining("réessayez dans 1 minute");
        assertThatCode(() -> limites.chatQuestion(3L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Mot de passe oublié : 5 demandes par 15 minutes depuis une même adresse")
    void forgotPasswordLimit_shouldRejectSixthRequest() {
        LimitesRequetes limites = new LimitesRequetes(5, 10, 10, 30, horloge);
        for (int i = 0; i < 5; i++) {
            limites.forgotPassword();
        }

        assertThatThrownBy(limites::forgotPassword).hasMessageContaining("minute");
        horloge.avancer(Duration.ofMinutes(15));
        assertThatCode(limites::forgotPassword).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Limite invalide (0) : refusée à la construction")
    void constructor_shouldRejectZeroLimit() {
        assertThatThrownBy(() -> new FenetreGlissante(0, Duration.ofMinutes(1), horloge))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
