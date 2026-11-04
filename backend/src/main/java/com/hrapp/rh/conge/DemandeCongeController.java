package com.hrapp.rh.conge;

import com.hrapp.rh.common.dto.PageReponse;
import com.hrapp.rh.securite.UtilisateurConnecte;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/conges/demandes")
@RequiredArgsConstructor
public class DemandeCongeController {

    private final DemandeCongeService service;
    private final JoursOuvrablesService joursOuvrablesService;

    // ---------- mes demandes (tout utilisateur connecté) ----------

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

    @PostMapping("/{id}/annulation")
    public DemandeCongeResponse cancel(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                       @PathVariable Long id) {
        return service.cancel(utilisateur, id);
    }

    // ---------- manager et RH ----------

    /** Boîte de réception du manager. Exemple : GET /api/conges/demandes/equipe?statut=EN_ATTENTE */
    @GetMapping("/equipe")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','RH')")
    public PageReponse<DemandeCongeResponse> listTeam(
            @AuthenticationPrincipal UtilisateurConnecte utilisateur,
            @RequestParam(required = false) StatutDemandeConge statut,
            @RequestParam(required = false) Integer annee,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.listTeam(utilisateur, statut, annee, page, size);
    }

    /** Vue globale RH. Exemple : GET /api/conges/demandes?statut=EN_ATTENTE&departementId=1&du=2026-12-01&au=2026-12-31 */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public PageReponse<DemandeCongeResponse> listAll(
            @RequestParam(required = false) StatutDemandeConge statut,
            @RequestParam(required = false) Long employeId,
            @RequestParam(required = false) Long departementId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.listAll(statut, employeId, departementId, du, au, page, size);
    }

    /** Détail : le demandeur, son manager d'équipe, ADMIN et RH. */
    @GetMapping("/{id}")
    public DemandeCongeResponse getById(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                        @PathVariable Long id) {
        return service.findById(utilisateur, id);
    }

    /** Approuve. Le corps est facultatif : {"motif": "..."}. */
    @PostMapping("/{id}/validation")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','RH')")
    public DemandeCongeResponse approve(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                        @PathVariable Long id,
                                        @RequestBody(required = false) @Valid DecisionRequest requete) {
        return service.approve(utilisateur, id, requete == null ? null : requete.motif());
    }

    /** Refuse. Le motif est obligatoire. */
    @PostMapping("/{id}/refus")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','RH')")
    public DemandeCongeResponse reject(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                       @PathVariable Long id,
                                       @Valid @RequestBody RefusRequest requete) {
        return service.reject(utilisateur, id, requete.motif());
    }
}