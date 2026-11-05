package com.hrapp.rh.securite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compteur à fenêtre glissante, par clé : « au plus N événements sur la durée D ».
 * Brique commune de toutes les limites de débit (connexion, mot de passe oublié, chat...).
 * Mémoire locale : valable pour une seule instance de l'application.
 */
public final class FenetreGlissante {

    private final Map<String, Deque<Instant>> evenements = new ConcurrentHashMap<>();
    private final int maximum;
    private final Duration duree;
    private final Clock clock;

    public FenetreGlissante(int maximum, Duration duree, Clock clock) {
        if (maximum < 1) {
            throw new IllegalArgumentException("La limite doit être d'au moins 1");
        }
        this.maximum = maximum;
        this.duree = duree;
        this.clock = clock;
    }

    /** Attente restante si la limite est atteinte pour cette clé, sinon vide. N'enregistre rien. */
    public Optional<Duration> blockedFor(String cle) {
        Deque<Instant> liste = evenements.get(cle);
        if (liste == null) {
            return Optional.empty();
        }
        synchronized (liste) {
            Instant maintenant = clock.instant();
            purge(liste, maintenant);
            if (liste.size() < maximum) {
                return Optional.empty();
            }
            return Optional.of(Duration.between(maintenant, liste.peekFirst().plus(duree)));
        }
    }

    /** Enregistre un événement pour cette clé. */
    public void record(String cle) {
        Deque<Instant> liste = evenements.computeIfAbsent(cle, k -> new ArrayDeque<>());
        synchronized (liste) {
            Instant maintenant = clock.instant();
            purge(liste, maintenant);
            liste.addLast(maintenant);
        }
    }

    /** Vérifie puis enregistre en une fois : vide si l'événement est accepté, sinon l'attente restante. */
    public Optional<Duration> tryAcquire(String cle) {
        Deque<Instant> liste = evenements.computeIfAbsent(cle, k -> new ArrayDeque<>());
        synchronized (liste) {
            Instant maintenant = clock.instant();
            purge(liste, maintenant);
            if (liste.size() >= maximum) {
                return Optional.of(Duration.between(maintenant, liste.peekFirst().plus(duree)));
            }
            liste.addLast(maintenant);
            return Optional.empty();
        }
    }

    public void clear(String cle) {
        evenements.remove(cle);
    }

    public void clearAll() {
        evenements.clear();
    }

    /** Oublie les clés dont tous les événements sont sortis de la fenêtre (mémoire bornée). */
    public void purgeExpired() {
        Instant maintenant = clock.instant();
        evenements.entrySet().removeIf(entree -> {
            synchronized (entree.getValue()) {
                purge(entree.getValue(), maintenant);
                return entree.getValue().isEmpty();
            }
        });
    }

    int size() {
        return evenements.size();
    }

    private void purge(Deque<Instant> liste, Instant maintenant) {
        while (!liste.isEmpty() && !liste.peekFirst().plus(duree).isAfter(maintenant)) {
            liste.pollFirst();
        }
    }
}
