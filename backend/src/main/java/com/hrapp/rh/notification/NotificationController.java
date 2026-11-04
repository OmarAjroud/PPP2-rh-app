package com.hrapp.rh.notification;

import com.hrapp.rh.common.dto.PageReponse;
import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Mes notifications : chacun ne voit que les siennes. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

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
}
