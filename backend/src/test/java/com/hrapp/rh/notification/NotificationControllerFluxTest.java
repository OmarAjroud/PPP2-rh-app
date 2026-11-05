package com.hrapp.rh.notification;

import com.hrapp.rh.securite.LimitesRequetes;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationControllerFluxTest {

    @Mock private NotificationService service;
    @Mock private FluxTicketService ticketService;
    @Mock private FluxNotificationRegistry registry;
    @Mock private LimitesRequetes limites;
    @InjectMocks private NotificationController controller;

    private final UtilisateurConnecte karim =
            new UtilisateurConnecte(3L, 3L, 1L, "manager", "hash", Roles.MANAGER, true);

    @Test
    @DisplayName("Ticket : émis pour l'employé connecté, avec sa durée de validité")
    void issueStreamTicket_shouldReturnTicketForConnectedEmployee() {
        given(ticketService.issue(3L)).willReturn("abc");

        assertThat(controller.issueStreamTicket(karim))
                .containsEntry("ticket", "abc")
                .containsEntry("expireDans", 30L);
    }

    @Test
    @DisplayName("Flux : ouvert avec un ticket valide (EventSource, sans en-tête)")
    void stream_shouldOpen_withValidTicket() {
        given(ticketService.consume("abc")).willReturn(Optional.of(3L));
        given(registry.open(3L)).willReturn(new SseEmitter());

        ResponseEntity<SseEmitter> reponse = controller.stream(null, "abc");

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reponse.getBody()).isNotNull();
        verify(service).countUnread(3L);                         // état initial de la cloche
    }

    @Test
    @DisplayName("Flux : ouvert avec un JWT dans l'en-tête, sans ticket")
    void stream_shouldOpen_withBearerToken() {
        given(registry.open(3L)).willReturn(new SseEmitter());

        assertThat(controller.stream(karim, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(ticketService, never()).consume(any());
    }

    @Test
    @DisplayName("Flux : 401 sans ticket valide ni JWT, et aucune connexion ouverte")
    void stream_shouldReturn401_withoutValidTicket() {
        given(ticketService.consume("perime")).willReturn(Optional.empty());

        ResponseEntity<SseEmitter> reponse = controller.stream(null, "perime");

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(registry, never()).open(any());
    }
}
