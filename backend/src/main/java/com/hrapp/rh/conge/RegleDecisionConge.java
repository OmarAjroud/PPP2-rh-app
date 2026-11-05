package com.hrapp.rh.conge;

import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.employe.EmployeRepository;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import com.hrapp.rh.utilisateur.Utilisateur;
import com.hrapp.rh.utilisateur.UtilisateurRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Qui décide d'une demande de congé : le manager direct d'abord, les RH (et l'ADMIN) en recours.
 *
 * <ul>
 *   <li>Le <b>manager direct</b> de l'employé décide, s'il peut agir : rôle MANAGER, RH ou ADMIN et compte actif.</li>
 *   <li>Les <b>RH et l'ADMIN</b> voient toutes les demandes, mais ne décident qu'en <b>recours</b> :
 *       l'employé n'a pas de manager, son manager ne peut pas agir, ou la demande attend depuis plus de
 *       {@code app.leave.escalation-days} jours.</li>
 *   <li>Personne ne décide de sa propre demande.</li>
 * </ul>
 * Toute la règle est ici : validation, refus, notifications, relances et « demandes à décider » s'en servent.
 */
@Component
public class RegleDecisionConge {

    /** Rôles qui peuvent décider des congés de leur équipe. */
    private static final Set<String> ROLES_DECIDEURS = Set.of(Roles.MANAGER, Roles.RH, Roles.ADMIN);

    private final UtilisateurRepository utilisateurRepository;
    private final EmployeRepository employeRepository;
    private final CongeProperties properties;
    private final Clock clock;

    public RegleDecisionConge(UtilisateurRepository utilisateurRepository, EmployeRepository employeRepository,
                              CongeProperties properties, Clock clock) {
        this.utilisateurRepository = utilisateurRepository;
        this.employeRepository = employeRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Pourquoi les RH peuvent décider de cette demande (vide : c'est au manager). */
    public enum Recours {
        SANS_MANAGER, MANAGER_INDISPONIBLE, DELAI_DEPASSE
    }

    /** Le manager direct de l'employé, s'il est en mesure de décider. */
    public Optional<Employe> activeManager(DemandeConge demande) {
        Employe manager = demande.getEmploye().getManager();
        if (manager == null || !ROLES_DECIDEURS.contains(manager.getRole().getNom())) {
            return Optional.empty();
        }
        boolean compteActif = utilisateurRepository.findByEmployeId(manager.getId())
                .map(u -> u.isActif() && !u.isEnAttenteActivation())
                .orElse(false);
        return compteActif ? Optional.of(manager) : Optional.empty();
    }

    /** Les RH peuvent-elles décider de cette demande, et pourquoi ? */
    public Optional<Recours> recours(DemandeConge demande) {
        if (demande.getEmploye().getManager() == null) {
            return Optional.of(Recours.SANS_MANAGER);
        }
        if (activeManager(demande).isEmpty()) {
            return Optional.of(Recours.MANAGER_INDISPONIBLE);
        }
        if (isOverdue(demande)) {
            return Optional.of(Recours.DELAI_DEPASSE);
        }
        return Optional.empty();
    }

    /** La demande attend-elle sa décision depuis plus longtemps que le délai ? */
    public boolean isOverdue(DemandeConge demande) {
        return demande.getStatut() == StatutDemandeConge.EN_ATTENTE
                && !demande.getDateCreation().plusDays(properties.escalationDays()).isAfter(LocalDateTime.now(clock));
    }

    /** À partir de quand les RH pourront décider si le manager ne l'a pas fait. */
    public LocalDateTime escalationDate(DemandeConge demande) {
        return demande.getDateCreation().plusDays(properties.escalationDays());
    }

    /** Cet utilisateur peut-il valider ou refuser cette demande ? (Sa propre demande : jamais.) */
    public boolean canDecide(UtilisateurConnecte acteur, DemandeConge demande) {
        if (isRequester(demande, acteur.getEmployeId())) {
            return false;
        }
        boolean estLeManager = activeManager(demande)
                .map(m -> m.getId().equals(acteur.getEmployeId()))
                .orElse(false);
        return estLeManager || (acteur.isAdminOrRh() && recours(demande).isPresent());
    }

    /**
     * Qui doit être prévenu qu'une décision est attendue : le manager s'il peut agir, sinon les décideurs de recours.
     * Le demandeur n'est jamais destinataire.
     */
    public List<Employe> deciders(DemandeConge demande) {
        Optional<Employe> manager = activeManager(demande);
        if (manager.isPresent() && !isRequester(demande, manager.get().getId())) {
            return List.of(manager.get());
        }
        return fallbackDeciders(demande);
    }

    /** Les décideurs de recours : les RH, ou à défaut les administrateurs (jamais le demandeur). */
    public List<Employe> fallbackDeciders(DemandeConge demande) {
        Map<Long, Employe> destinataires = new LinkedHashMap<>();
        addActiveExceptRequester(destinataires, employeRepository.findByRoleNom(Roles.RH), demande);
        if (destinataires.isEmpty()) {
            addActiveExceptRequester(destinataires, employeRepository.findByRoleNom(Roles.ADMIN), demande);
        }
        return new ArrayList<>(destinataires.values());
    }

    // ---------- méthodes privées ----------

    private void addActiveExceptRequester(Map<Long, Employe> cible, List<Employe> candidats, DemandeConge demande) {
        for (Employe candidat : candidats) {
            if (!isRequester(demande, candidat.getId()) && hasActiveAccount(candidat)) {
                cible.putIfAbsent(candidat.getId(), candidat);
            }
        }
    }

    private boolean hasActiveAccount(Employe employe) {
        return utilisateurRepository.findByEmployeId(employe.getId())
                .filter(Utilisateur::isActif)
                .filter(u -> !u.isEnAttenteActivation())
                .isPresent();
    }

    private static boolean isRequester(DemandeConge demande, Long employeId) {
        return demande.getEmploye().getId().equals(employeId);
    }
}
