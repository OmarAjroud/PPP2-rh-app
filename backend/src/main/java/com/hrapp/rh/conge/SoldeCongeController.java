package com.hrapp.rh.conge;

import com.hrapp.rh.securite.UtilisateurConnecte;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conges/soldes")
@RequiredArgsConstructor
public class SoldeCongeController {

    private final SoldeCongeService service;

    /** Mes soldes de l'année (l'année en cours par défaut). */
    @GetMapping("/moi")
    public List<SoldeResponse> mine(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                    @RequestParam(required = false) Integer annee) {
        return service.findMine(utilisateur, annee);
    }

    /** Mon historique de mouvements (allocation, déductions, remboursements, ajustements). */
    @GetMapping("/moi/mouvements")
    public List<MouvementSoldeResponse> myHistory(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                                  @RequestParam(required = false) Integer annee) {
        return service.historyMine(utilisateur, annee);
    }

    /** Soldes d'un employé : lui-même, son manager d'équipe, ADMIN/RH (404 pour les autres). */
    @GetMapping("/{employeId}")
    public List<SoldeResponse> forEmployee(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                           @PathVariable Long employeId,
                                           @RequestParam(required = false) Integer annee) {
        return service.findForEmployee(utilisateur, employeId, annee);
    }

    @GetMapping("/{employeId}/mouvements")
    public List<MouvementSoldeResponse> historyForEmployee(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                                           @PathVariable Long employeId,
                                                           @RequestParam(required = false) Integer annee) {
        return service.historyForEmployee(utilisateur, employeId, annee);
    }

    /** Ajustement du solde alloué par les RH (commentaire obligatoire, jamais pour soi-même). */
    @PostMapping("/{employeId}/ajustements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public MouvementSoldeResponse adjust(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                         @PathVariable Long employeId,
                                         @Valid @RequestBody AjustementSoldeRequest requete) {
        return service.adjust(utilisateur, employeId, requete);
    }
}