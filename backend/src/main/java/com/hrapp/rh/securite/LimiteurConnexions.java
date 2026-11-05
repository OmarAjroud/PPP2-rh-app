package com.hrapp.rh.securite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freine la recherche de mot de passe par essais successifs : après N échecs pour un même compte
 * depuis une même adresse IP dans la fenêtre, les tentatives suivantes sont refusées (429) jusqu'à
 * ce que le plus ancien échec sorte de la fenêtre.
 *
 * La clé « compte + IP » empêche un inconnu de bloquer un utilisateur réel partout (déni de service),
 * tout en arrêtant les essais répétés. Mémoire locale : valable pour une seule instance de l'application.
 */
@Component
public class LimiteurConnexions {

    private final Map<String, Deque<Instant>> echecs = new ConcurrentHashMap<>();
    private final int maxEchecs;
    private final Duration fenetre;
    private final Clock clock;

    public LimiteurConnexions(@Value("${app.security.login.max-failures:5}") int maxEchecs,
                              @Value("${app.security.login.window-minutes:15}") long fenetreMinutes,
                              Clock clock) {
        this.maxEchecs = maxEchecs;
        this.fenetre = Duration.ofMinutes(fenetreMinutes);
        this.clock = clock;
    }

    /** Durée d'attente restante si ce compte est bloqué depuis cette adresse, sinon vide. */
    public Optional<Duration> blockedFor(String nomUtilisateur) {
        Deque<Instant> liste = echecs.get(key(nomUtilisateur));
        if (liste == null) {
            return Optional.empty();
        }
        synchronized (liste) {
            Instant maintenant = clock.instant();
            purge(liste, maintenant);
            if (liste.size() < maxEchecs) {
                return Optional.empty();
            }
            return Optional.of(Duration.between(maintenant, liste.peekFirst().plus(fenetre)));
        }
    }

    public void recordFailure(String nomUtilisateur) {
        Deque<Instant> liste = echecs.computeIfAbsent(key(nomUtilisateur), k -> new ArrayDeque<>());
        synchronized (liste) {
            Instant maintenant = clock.instant();
            purge(liste, maintenant);
            liste.addLast(maintenant);
        }
    }

    /** Connexion réussie : le compteur de ce compte (depuis cette adresse) repart à zéro. */
    public void recordSuccess(String nomUtilisateur) {
        echecs.remove(key(nomUtilisateur));
    }

    /** Oublie les compteurs dont tous les échecs sont sortis de la fenêtre (mémoire bornée). */
    @Scheduled(fixedRate = 300_000)
    public void purgeExpired() {
        Instant maintenant = clock.instant();
        echecs.entrySet().removeIf(entree -> {
            synchronized (entree.getValue()) {
                purge(entree.getValue(), maintenant);
                return entree.getValue().isEmpty();
            }
        });
    }

    /** Oublie tous les compteurs (tests d'intégration : chaque test repart de zéro, comme la base). */
    public void reset() {
        echecs.clear();
    }

    int trackedKeys() {
        return echecs.size();
    }

    // ---------- méthodes privées ----------

    private void purge(Deque<Instant> liste, Instant maintenant) {
        while (!liste.isEmpty() && !liste.peekFirst().plus(fenetre).isAfter(maintenant)) {
            liste.pollFirst();
        }
    }

    private static String key(String nomUtilisateur) {
        String compte = nomUtilisateur == null ? "" : nomUtilisateur.trim().toLowerCase(Locale.ROOT);
        return compte + "|" + clientIp();
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributs) {
            return attributs.getRequest().getRemoteAddr();      // tient compte de X-Forwarded-For si le proxy est déclaré
        }
        return "local";
    }
}
