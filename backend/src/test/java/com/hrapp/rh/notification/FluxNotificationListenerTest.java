package com.hrapp.rh.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FluxNotificationListenerTest {

    @Mock private FluxNotificationRegistry registry;
    @Mock private NotificationRepository repository;
    @Mock private FluxTicketService ticketService;
    @InjectMocks private FluxNotificationListener listener;

    private final NotificationResponse notification = new NotificationResponse(1L, TypeNotification.CONGE_DEMANDE,
            "Nouvelle demande de congé", "Lina Gharbi a demandé…", "/conges/demandes/10", false,
            LocalDateTime.of(2026, 11, 5, 10, 0), null);

    @Test
    @DisplayName("Nouvelle notification : poussée avec le compteur à jour")
    void onCreated_shouldPushNotificationWithUnreadCount() {
        given(registry.countConnections(3L)).willReturn(2);
        given(repository.countByDestinataireIdAndLueFalse(3L)).willReturn(4L);

        listener.onCreated(new NotificationEvenements.Creee(3L, notification));

        verify(registry).send(3L, "notification", new FluxNotificationListener.NotificationPoussee(notification, 4L));
    }

    @Test
    @DisplayName("Nouvelle notification : rien n'est calculé si le destinataire n'est pas connecté")
    void onCreated_shouldSkip_whenRecipientIsNotConnected() {
        given(registry.countConnections(3L)).willReturn(0);

        listener.onCreated(new NotificationEvenements.Creee(3L, notification));

        verify(repository, never()).countByDestinataireIdAndLueFalse(anyLong());
        verify(registry, never()).send(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("Lecture : le nouveau compteur est poussé aux autres onglets")
    void onUnreadChanged_shouldPushNewCount() {
        given(registry.countConnections(3L)).willReturn(1);
        given(repository.countByDestinataireIdAndLueFalse(3L)).willReturn(0L);

        listener.onUnreadChanged(new NotificationEvenements.NonLuesModifiees(3L));

        verify(registry).send(3L, "non-lues", Map.of("nombre", 0L));
    }

    @Test
    @DisplayName("Tâche planifiée : battement de cœur et purge des tickets expirés")
    void heartbeat_shouldPingAndPurgeTickets() {
        listener.heartbeat();

        verify(registry).heartbeat();
        verify(ticketService).purgeExpired();
    }
}
