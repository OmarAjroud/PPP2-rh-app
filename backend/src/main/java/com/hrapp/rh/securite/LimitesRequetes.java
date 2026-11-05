package com.hrapp.rh.securite;

import com.hrapp.rh.common.exception.TropDeTentativesException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Limites de débit des opérations coûteuses ou exposées (au-delà : 429 avec Retry-After).
 * <ul>
 *   <li>mot de passe oublié : par adresse IP (évite d'inonder des boîtes mail via l'application) ;</li>
 *   <li>activation et réinitialisation par lien : par adresse IP ;</li>
 *   <li>questions au chat : par utilisateur (chaque réponse occupe le modèle plusieurs secondes) ;</li>
 *   <li>tickets du flux SSE : par utilisateur (gardés en mémoire).</li>
 * </ul>
 * Mémoire locale : valable pour une seule instance de l'application.
 */
@Component
public class LimitesRequetes {

    private final FenetreGlissante motDePasseOublie;
    private final FenetreGlissante liensEmail;
    private final FenetreGlissante chat;
    private final FenetreGlissante ticketsFlux;

    public LimitesRequetes(@Value("${app.security.rate.forgot-password-per-ip:5}") int motDePasseOubliee,
                           @Value("${app.security.rate.email-links-per-ip:10}") int liens,
                           @Value("${app.security.rate.chat-per-user-per-minute:10}") int questions,
                           @Value("${app.security.rate.stream-tickets-per-user-per-minute:30}") int tickets,
                           Clock clock) {
        this.motDePasseOublie = new FenetreGlissante(motDePasseOubliee, Duration.ofMinutes(15), clock);
        this.liensEmail = new FenetreGlissante(liens, Duration.ofMinutes(15), clock);
        this.chat = new FenetreGlissante(questions, Duration.ofMinutes(1), clock);
        this.ticketsFlux = new FenetreGlissante(tickets, Duration.ofMinutes(1), clock);
    }

    public void forgotPassword() {
        acquire(motDePasseOublie, AdresseClient.courante(), "Trop de demandes de réinitialisation");
    }

    public void emailLink() {
        acquire(liensEmail, AdresseClient.courante(), "Trop de tentatives");
    }

    public void chatQuestion(Long utilisateurId) {
        acquire(chat, String.valueOf(utilisateurId), "Trop de questions à l'assistant");
    }

    public void streamTicket(Long utilisateurId) {
        acquire(ticketsFlux, String.valueOf(utilisateurId), "Trop de demandes de ticket");
    }

    @Scheduled(fixedRate = 300_000)
    public void purgeExpired() {
        motDePasseOublie.purgeExpired();
        liensEmail.purgeExpired();
        chat.purgeExpired();
        ticketsFlux.purgeExpired();
    }

    /** Oublie tous les compteurs (tests d'intégration). */
    public void reset() {
        motDePasseOublie.clearAll();
        liensEmail.clearAll();
        chat.clearAll();
        ticketsFlux.clearAll();
    }

    private static void acquire(FenetreGlissante fenetre, String cle, String message) {
        fenetre.tryAcquire(cle).ifPresent(attente -> {
            long secondes = Math.max(1, attente.toSeconds());
            String delai = secondes < 60 ? secondes + " seconde(s)" : ((secondes + 59) / 60) + " minute(s)";
            throw new TropDeTentativesException(message + " : réessayez dans " + delai, attente);
        });
    }
}
