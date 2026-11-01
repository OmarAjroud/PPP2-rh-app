package com.hrapp.rh.utilisateur;

import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employes/{employeId}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','RH')")
public class CompteController {

    private final CompteService service;

    /** L'employé a-t-il un compte, et dans quel état ? (404 s'il n'en a pas) */
    @GetMapping("/compte")
    public CompteResponse getAccount(@PathVariable Long employeId) {
        return service.findAccount(employeId);
    }

    /** Crée le compte si besoin et envoie (ou renvoie) l'email d'activation. */
    @PostMapping("/invitation")
    public CompteResponse invite(@AuthenticationPrincipal UtilisateurConnecte acteur,
                                 @PathVariable Long employeId) {
        return service.invite(acteur, employeId);
    }
}