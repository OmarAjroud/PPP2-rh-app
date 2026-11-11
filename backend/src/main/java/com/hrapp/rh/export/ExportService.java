package com.hrapp.rh.export;

import com.hrapp.rh.audit.AuditEntryResponse;
import com.hrapp.rh.audit.AuditFilter;
import com.hrapp.rh.audit.AuditService;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.conge.DemandeCongeResponse;
import com.hrapp.rh.conge.DemandeCongeService;
import com.hrapp.rh.conge.StatutDemandeConge;
import com.hrapp.rh.departement.Departement;
import com.hrapp.rh.departement.DepartementRepository;
import com.hrapp.rh.departement.DepartementResponse;
import com.hrapp.rh.departement.DepartementService;
import com.hrapp.rh.employe.EmployeResponse;
import com.hrapp.rh.employe.EmployeService;
import com.hrapp.rh.role.Role;
import com.hrapp.rh.role.RoleRepository;
import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import static com.hrapp.rh.export.Colonne.Type.DATE;
import static com.hrapp.rh.export.Colonne.Type.DATE_HEURE;
import static com.hrapp.rh.export.Colonne.Type.NOMBRE;
import static com.hrapp.rh.export.Colonne.Type.TEXTE;

/**
 * Construit le contenu des exports à partir des mêmes services que les listes : mêmes filtres et mêmes droits
 * (un manager n'exporte que ce qu'il voit). Les colonnes sont celles des écrans ; jamais de salaire.
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    /** Au-delà, l'utilisateur affine ses filtres (un fichier énorme serait illisible et coûteux à produire). */
    static final int MAX_LIGNES = 5000;

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmployeService employeService;
    private final DepartementService departementService;
    private final DemandeCongeService demandeCongeService;
    private final AuditService auditService;
    private final DepartementRepository departementRepository;
    private final RoleRepository roleRepository;

    public Tableau employes(UtilisateurConnecte utilisateur, String recherche, Long departementId, Long roleId) {
        List<EmployeResponse> employes = borner(employeService.searchAll(utilisateur, recherche, departementId, roleId, MAX_LIGNES + 1));
        List<List<Object>> lignes = new ArrayList<>();
        for (EmployeResponse e : employes) {
            lignes.add(Arrays.asList(e.id(), e.nom(), e.prenom(), e.email(), e.departementNom(), libelleRole(e.roleNom()), e.telephone()));
        }
        Filtres f = new Filtres()
                .ajouter("Recherche", recherche)
                .ajouter("Département", nomDepartement(departementId))
                .ajouter("Rôle", libelleRole(nomRole(roleId)));
        return new Tableau("Employés", f.texte(), List.of(
                new Colonne("ID", NOMBRE, 6),
                new Colonne("Nom", TEXTE, 18),
                new Colonne("Prénom", TEXTE, 18),
                new Colonne("Email", TEXTE, 32),
                new Colonne("Département", TEXTE, 22),
                new Colonne("Rôle", TEXTE, 18),
                new Colonne("Téléphone", TEXTE, 17)), lignes);
    }

    public Tableau departements() {
        List<List<Object>> lignes = new ArrayList<>();
        for (DepartementResponse d : borner(departementService.findAll())) {
            lignes.add(Arrays.asList(d.id(), d.nom(), d.description(), d.nombreEmployes()));
        }
        return new Tableau("Départements", null, List.of(
                new Colonne("ID", NOMBRE, 6),
                new Colonne("Nom", TEXTE, 24),
                new Colonne("Description", TEXTE, 50),
                new Colonne("Employés", NOMBRE, 10)), lignes);
    }

    public Tableau demandes(StatutDemandeConge statut, Long employeId, Long departementId, LocalDate du, LocalDate au) {
        List<DemandeCongeResponse> demandes = borner(demandeCongeService.listAllUnpaged(statut, employeId, departementId, du, au, MAX_LIGNES + 1));
        List<List<Object>> lignes = new ArrayList<>();
        for (DemandeCongeResponse d : demandes) {
            lignes.add(Arrays.asList(d.id(), d.employeNomComplet(), d.employeDepartementNom(), d.typeCongeLibelle(),
                    d.dateDebut(), d.dateFin(), d.nombreJours(), d.statut().getLibelle(), d.dateCreation(),
                    d.decideParNomComplet(), d.motifDecision()));
        }
        Filtres f = new Filtres()
                .ajouter("Statut", statut == null ? null : statut.getLibelle())
                .ajouter("Employé n°", employeId == null ? null : employeId.toString())
                .ajouter("Département", nomDepartement(departementId))
                .ajouter("Période", periode(du, au));
        return new Tableau("Demandes de congé", f.texte(), List.of(
                new Colonne("N°", NOMBRE, 6),
                new Colonne("Employé", TEXTE, 22),
                new Colonne("Département", TEXTE, 18),
                new Colonne("Type", TEXTE, 16),
                new Colonne("Du", DATE, 11),
                new Colonne("Au", DATE, 11),
                new Colonne("Jours", NOMBRE, 7),
                new Colonne("Statut", TEXTE, 11),
                new Colonne("Déposée le", DATE_HEURE, 15),
                new Colonne("Décidée par", TEXTE, 18),
                new Colonne("Motif de la décision", TEXTE, 26)), lignes);
    }

    public Tableau audit(AuditFilter filtre) {
        List<AuditEntryResponse> entrees = borner(auditService.searchAll(filtre, MAX_LIGNES + 1));
        List<List<Object>> lignes = new ArrayList<>();
        for (AuditEntryResponse a : entrees) {
            lignes.add(Arrays.asList(a.dateAction(), a.nomUtilisateur(), libelleAction(a.action()), a.entite(),
                    a.entiteId(), a.details(), a.adresseIp()));
        }
        Filtres f = new Filtres()
                .ajouter("Utilisateur", filtre.nomUtilisateur())
                .ajouter("Action", filtre.action() == null ? null : libelleAction(filtre.action().name()))
                .ajouter("Entité", filtre.entite())
                .ajouter("N°", filtre.entiteId())
                .ajouter("Période", periode(filtre.du(), filtre.au()));
        return new Tableau("Journal d'audit", f.texte(), List.of(
                new Colonne("Date", DATE_HEURE, 15),
                new Colonne("Utilisateur", TEXTE, 14),
                new Colonne("Action", TEXTE, 16),
                new Colonne("Entité", TEXTE, 14),
                new Colonne("N°", TEXTE, 8),
                new Colonne("Détails", TEXTE, 40),
                new Colonne("Adresse IP", TEXTE, 14)), lignes);
    }

    private static <T> List<T> borner(List<T> liste) {
        if (liste.size() > MAX_LIGNES) {
            throw new RequeteInvalideException("L'export est limité à " + String.format(Locale.FRANCE, "%,d", MAX_LIGNES)
                    + " lignes : affinez les filtres");
        }
        return liste;
    }

    private String nomDepartement(Long id) {
        return id == null ? null : departementRepository.findById(id).map(Departement::getNom).orElse("n° " + id);
    }

    private String nomRole(Long id) {
        return id == null ? null : roleRepository.findById(id).map(Role::getNom).orElse("n° " + id);
    }

    private static String periode(LocalDate du, LocalDate au) {
        if (du == null && au == null) {
            return null;
        }
        if (du == null) {
            return "jusqu'au " + au.format(JOUR);
        }
        return au == null ? "depuis le " + du.format(JOUR) : "du " + du.format(JOUR) + " au " + au.format(JOUR);
    }

    /** Same French names as the screens; a role created by an administrator keeps its own name. */
    static String libelleRole(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case "ADMIN" -> "Administrateur";
            case "RH" -> "Ressources humaines";
            case "MANAGER" -> "Manager";
            case "EMPLOYE" -> "Employé";
            default -> role;
        };
    }

    /** "CHANGEMENT_MOT_DE_PASSE" → "Changement mot de passe". */
    static String libelleAction(String action) {
        if (action == null || action.isBlank()) {
            return action;
        }
        String texte = action.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
    }

    /** Filtres appliqués, en clair : « Recherche : lina · Département : Informatique ». */
    private static final class Filtres {
        private final StringJoiner joint = new StringJoiner(" · ");

        Filtres ajouter(String nom, String valeur) {
            if (valeur != null && !valeur.isBlank()) {
                joint.add(nom + " : " + valeur.trim());
            }
            return this;
        }

        String texte() {
            return joint.length() == 0 ? null : joint.toString();
        }
    }
}
