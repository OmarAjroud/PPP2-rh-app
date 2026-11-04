package com.hrapp.rh.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Connexions SSE ouvertes, par employé (un employé peut avoir plusieurs onglets ouverts).
 * Une connexion qui échoue à l'envoi est retirée : c'est ainsi qu'on détecte un client parti.
 */
@Slf4j
@Component
public class FluxNotificationRegistry {

    /** Au-delà, la connexion la plus ancienne est fermée (onglets oubliés, client qui boucle). */
    static final int MAX_CONNEXIONS_PAR_EMPLOYE = 5;
    /** Délai de reconnexion conseillé au navigateur. */
    private static final long RECONNEXION_MS = 5_000;

    private final Map<Long, List<SseEmitter>> connexions = new ConcurrentHashMap<>();
    private final long dureeMs;

    /** Un flux vit aussi longtemps qu'un jeton d'accès : le client se reconnecte ensuite avec un nouveau ticket. */
    public FluxNotificationRegistry(@Value("${app.security.jwt.access-token-minutes}") long accessMinutes) {
        this.dureeMs = accessMinutes * 60_000;
    }

    /** Ouvre un flux pour cet employé. */
    public SseEmitter open(Long employeId) {
        SseEmitter emitter = createEmitter(dureeMs);
        List<SseEmitter> liste = connexions.computeIfAbsent(employeId, id -> new CopyOnWriteArrayList<>());
        liste.add(emitter);
        while (liste.size() > MAX_CONNEXIONS_PAR_EMPLOYE) {
            SseEmitter plusAncienne = liste.removeFirst();
            plusAncienne.complete();
        }

        Runnable retirer = () -> remove(employeId, emitter);
        emitter.onCompletion(retirer);
        emitter.onTimeout(emitter::complete);    // fin normale : pas d'AsyncRequestTimeoutException
        emitter.onError(e -> retirer.run());
        return emitter;
    }

    /** Envoie un événement à toutes les connexions de cet employé. */
    public void send(Long employeId, String nomEvenement, Object donnees) {
        List<SseEmitter> liste = connexions.get(employeId);
        if (liste == null) {
            return;
        }
        for (SseEmitter emitter : liste) {
            trySend(employeId, emitter, SseEmitter.event()
                    .name(nomEvenement)
                    .reconnectTime(RECONNEXION_MS)
                    .data(donnees, MediaType.APPLICATION_JSON));
        }
    }

    /** Commentaire SSE envoyé à tout le monde : garde les connexions ouvertes à travers les proxys et purge les mortes. */
    public void heartbeat() {
        connexions.forEach((employeId, liste) ->
                liste.forEach(emitter -> trySend(employeId, emitter, SseEmitter.event().comment("ping"))));
    }

    public int countConnections(Long employeId) {
        List<SseEmitter> liste = connexions.get(employeId);
        return liste == null ? 0 : liste.size();
    }

    /** Point d'extension pour les tests. */
    SseEmitter createEmitter(long timeoutMs) {
        return new SseEmitter(timeoutMs);
    }

    // ---------- méthodes privées ----------

    private void trySend(Long employeId, SseEmitter emitter, SseEmitter.SseEventBuilder evenement) {
        try {
            emitter.send(evenement);
        } catch (IOException | IllegalStateException e) {
            // client déconnecté (onglet fermé, réseau coupé) : on oublie cette connexion
            log.debug("Flux SSE fermé pour l'employé {} : {}", employeId, e.getMessage());
            remove(employeId, emitter);
            emitter.completeWithError(e);
        }
    }

    private void remove(Long employeId, SseEmitter emitter) {
        connexions.computeIfPresent(employeId, (id, liste) -> {
            liste.remove(emitter);
            return liste.isEmpty() ? null : liste;
        });
    }
}
