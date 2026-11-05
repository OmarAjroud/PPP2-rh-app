package com.hrapp.rh.notification;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tickets d'ouverture du flux SSE.
 * EventSource (navigateur) ne peut pas envoyer d'en-tête Authorization : le client échange d'abord
 * son JWT contre un ticket à usage unique, valable 30 secondes, qu'il passe dans l'URL du flux.
 * Le JWT n'apparaît ainsi jamais dans une URL (ni dans les journaux d'accès).
 *
 * Stockage en mémoire : suffisant pour une seule instance de l'application.
 */
@Service
public class FluxTicketService {

    static final Duration VALIDITE = Duration.ofSeconds(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Ticket(Long employeId, Instant expiration) {}

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Clock clock;

    public FluxTicketService(Clock clock) {
        this.clock = clock;
    }

    /** Crée un ticket pour cet employé et renvoie sa valeur. */
    public String issue(Long employeId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String valeur = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(valeur, new Ticket(employeId, clock.instant().plus(VALIDITE)));
        return valeur;
    }

    /** Consomme un ticket : renvoie l'employé s'il est valide ; un ticket ne sert qu'une fois. */
    public Optional<Long> consume(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return Optional.empty();
        }
        Ticket ticket = tickets.remove(valeur.trim());           // retiré dans tous les cas : usage unique
        if (ticket == null || !ticket.expiration().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(ticket.employeId());
    }

    /** Supprime les tickets expirés jamais utilisés (appelé périodiquement). */
    public void purgeExpired() {
        Instant maintenant = clock.instant();
        tickets.values().removeIf(t -> !t.expiration().isAfter(maintenant));
    }

    int size() {
        return tickets.size();
    }
}
