package com.hrapp.rh.tableaudebord;

import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tableau-de-bord")
@RequiredArgsConstructor
public class TableauDeBordController {

    private final TableauDeBordService service;

    /**
     * Exemple : GET /api/tableau-de-bord?annee=2026&departementId=1
     * ADMIN / RH : complet (filtre département optionnel) ; MANAGER : son département, sans masse salariale.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RH','MANAGER')")
    public TableauDeBordResponse get(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                     @RequestParam(required = false) Integer annee,
                                     @RequestParam(required = false) Long departementId) {
        return service.build(utilisateur, annee, departementId);
    }
}
