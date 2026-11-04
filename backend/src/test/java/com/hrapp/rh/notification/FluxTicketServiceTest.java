package com.hrapp.rh.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FluxTicketServiceTest {

    /** Horloge réglable pour simuler l'écoulement du temps. */
    private static final class HorlogeReglable extends Clock {
        private Instant maintenant = Instant.parse("2026-11-05T10:00:00Z");

        void avancer(Duration duree) {
            maintenant = maintenant.plus(duree);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return maintenant; }
    }

    private final HorlogeReglable horloge = new HorlogeReglable();
    private final FluxTicketService service = new FluxTicketService(horloge);

    @Test
    @DisplayName("Un ticket valide donne l'employé, une seule fois")
    void consume_shouldReturnEmployee_onlyOnce() {
        String ticket = service.issue(4L);

        assertThat(service.consume(ticket)).contains(4L);
        assertThat(service.consume(ticket)).isEmpty();           // usage unique
    }

    @Test
    @DisplayName("Un ticket expiré (plus de 30 s) est refusé")
    void consume_shouldReject_expiredTicket() {
        String ticket = service.issue(4L);
        horloge.avancer(Duration.ofSeconds(31));

        assertThat(service.consume(ticket)).isEmpty();
    }

    @Test
    @DisplayName("Un ticket inconnu, vide ou absent est refusé")
    void consume_shouldReject_unknownOrBlankTicket() {
        assertThat(service.consume("inconnu")).isEmpty();
        assertThat(service.consume("  ")).isEmpty();
        assertThat(service.consume(null)).isEmpty();
    }

    @Test
    @DisplayName("Deux tickets sont toujours différents et assez longs pour ne pas être devinés")
    void issue_shouldCreateDistinctUnguessableTickets() {
        String a = service.issue(4L);
        String b = service.issue(4L);

        assertThat(a).isNotEqualTo(b).hasSizeGreaterThanOrEqualTo(43);   // 256 bits en Base64 URL
    }

    @Test
    @DisplayName("La purge retire les tickets expirés et garde les valides")
    void purgeExpired_shouldOnlyRemoveExpiredTickets() {
        service.issue(1L);
        horloge.avancer(Duration.ofSeconds(20));
        String recent = service.issue(2L);
        horloge.avancer(Duration.ofSeconds(15));                 // le premier a 35 s, le second 15 s

        service.purgeExpired();

        assertThat(service.size()).isEqualTo(1);
        assertThat(service.consume(recent)).contains(2L);
    }
}
