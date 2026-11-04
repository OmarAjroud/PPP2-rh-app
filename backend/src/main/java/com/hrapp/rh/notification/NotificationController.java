package com.hrapp.rh.notification;

import com.hrapp.rh.common.dto.PageReponse;
import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/** Mes notifications : chacun ne voit que les siennes. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;
    private final FluxTicketService ticketService;
    private final FluxNotificationRegistry registry;

    /** Exemple : GET /api/notifications?nonLues=true&page=0&size=10 */
    @GetMapping
    public PageReponse<NotificationResponse> list(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                                  @RequestParam(defaultValue = "false") boolean nonLues,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return service.listMine(utilisateur.getEmployeId(), nonLues, page, size);
    }

    /** Compteur de la cloche, à interroger régulièrement (ex. toutes les 30 secondes). */
    @GetMapping("/non-lues/nombre")
    public Map<String, Long> countUnread(@AuthenticationPrincipal UtilisateurConnecte utilisateur) {
        return Map.of("nombre", service.countUnread(utilisateur.getEmployeId()));
    }

    @PostMapping("/{id}/lecture")
    public NotificationResponse markRead(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                         @PathVariable Long id) {
        return service.markRead(utilisateur.getEmployeId(), id);
    }

    /** « Tout marquer comme lu ». */
    @PostMapping("/lecture")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal UtilisateurConnecte utilisateur) {
        return Map.of("nombre", service.markAllRead(utilisateur.getEmployeId()));
    }

    // ---------- temps réel (SSE) ----------

    /**
     * Étape 1 : échange le JWT (en-tête Authorization) contre un ticket d'ouverture du flux,
     * à usage unique et valable 30 secondes.
     */
    @PostMapping("/flux/ticket")
    public Map<String, Object> issueStreamTicket(@AuthenticationPrincipal UtilisateurConnecte utilisateur) {
        return Map.of("ticket", ticketService.issue(utilisateur.getEmployeId()),
                "expireDans", FluxTicketService.VALIDITE.toSeconds());
    }

    /**
     * Étape 2 : ouvre le flux. Exemple côté navigateur :
     * {@code new EventSource('/api/notifications/flux?ticket=...')}.
     * Accepte aussi un JWT dans l'en-tête Authorization (clients capables d'envoyer des en-têtes).
     * Événements : « non-lues » (dès l'ouverture, puis à chaque lecture) et « notification ».
     * Le flux se ferme après la durée de vie d'un jeton d'accès : demander alors un nouveau ticket.
     */
    @GetMapping(path = "/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                             @RequestParam(required = false) String ticket) {
        Optional<Long> employeId = utilisateur != null
                ? Optional.of(utilisateur.getEmployeId())
                : ticketService.consume(ticket);
        if (employeId.isEmpty()) {
            // pas de corps JSON : le client attend du text/event-stream ; EventSource voit l'échec et s'arrête
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        SseEmitter emitter = registry.open(employeId.get());
        try {
            // état initial : la cloche est juste, même après une reconnexion
            emitter.send(SseEmitter.event().name(FluxNotificationListener.EVENEMENT_NON_LUES)
                    .data(Map.of("nombre", service.countUnread(employeId.get())), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")      // nginx : ne pas mettre le flux en mémoire tampon
                .body(emitter);
    }
}
