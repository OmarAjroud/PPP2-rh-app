package com.hrapp.rh.conge;

import com.hrapp.rh.common.dto.PageReponse;
import com.hrapp.rh.securite.UtilisateurConnecte;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Tout utilisateur connecté gère ses PROPRES demandes ; les règles sont dans le service. */
@RestController
@RequestMapping("/api/conges/demandes")
@RequiredArgsConstructor
public class DemandeCongeController {

    private final DemandeCongeService service;
    private final JoursOuvrablesService joursOuvrablesService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DemandeCongeResponse create(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                       @Valid @RequestBody DemandeCongeRequest requete) {
        return service.create(utilisateur, requete);
    }

    /** Exemple : GET /api/conges/demandes/moi?statut=EN_ATTENTE&annee=2026&page=0&size=10 */
    @GetMapping("/moi")
    public PageReponse<DemandeCongeResponse> listMine(
            @AuthenticationPrincipal UtilisateurConnecte utilisateur,
            @RequestParam(required = false) StatutDemandeConge statut,
            @RequestParam(required = false) Integer annee,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.listMine(utilisateur, statut, annee, page, size);
    }

    /** Aperçu pour le formulaire : combien de jours ouvrables entre ces deux dates ? */
    @GetMapping("/jours-ouvrables")
    public JoursOuvrablesResponse countWorkingDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return joursOuvrablesService.calculate(dateDebut, dateFin);
    }

    @GetMapping("/{id}")
    public DemandeCongeResponse getById(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                        @PathVariable Long id) {
        return service.findById(utilisateur, id);
    }

    @PostMapping("/{id}/annulation")
    public DemandeCongeResponse cancel(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                       @PathVariable Long id) {
        return service.cancel(utilisateur, id);
    }
}