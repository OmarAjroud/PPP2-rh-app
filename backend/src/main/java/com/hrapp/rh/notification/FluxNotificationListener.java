package com.hrapp.rh.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Relaie les notifications en direct sur les flux SSE.
 * Après validation de la transaction seulement (une action annulée n'est jamais poussée),
 * et dans un autre thread (un client lent ne ralentit pas la requête qui a créé la notification).
 */
@Component
@RequiredArgsConstructor
public class FluxNotificationListener {

    static final String EVENEMENT_NOTIFICATION = "notification";
    static final String EVENEMENT_NON_LUES = "non-lues";

    /** Contenu de l'événement « notification » : la notification et le nouveau compteur de la cloche. */
    public record NotificationPoussee(NotificationResponse notification, long nombreNonLues) {}

    private final FluxNotificationRegistry registry;
    private final NotificationRepository repository;
    private final FluxTicketService ticketService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCreated(NotificationEvenements.Creee evenement) {
        Long destinataire = evenement.destinataireId();
        if (registry.countConnections(destinataire) == 0) {
            return;                                            // personne à l'écoute : pas de requête inutile
        }
        long nonLues = repository.countByDestinataireIdAndLueFalse(destinataire);
        registry.send(destinataire, EVENEMENT_NOTIFICATION,
                new NotificationPoussee(evenement.notification(), nonLues));
    }

    /** Une lecture dans un onglet met à jour la cloche des autres onglets. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUnreadChanged(NotificationEvenements.NonLuesModifiees evenement) {
        Long employeId = evenement.employeId();
        if (registry.countConnections(employeId) == 0) {
            return;
        }
        registry.send(employeId, EVENEMENT_NON_LUES,
                Map.of("nombre", repository.countByDestinataireIdAndLueFalse(employeId)));
    }

    @Scheduled(fixedRate = 25_000, initialDelay = 25_000)
    public void heartbeat() {
        registry.heartbeat();
        ticketService.purgeExpired();
    }
}
