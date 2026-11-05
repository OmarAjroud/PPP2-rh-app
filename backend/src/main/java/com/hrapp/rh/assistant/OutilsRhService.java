package com.hrapp.rh.assistant;

import com.hrapp.rh.audit.AuditAction;
import com.hrapp.rh.audit.AuditService;
import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.conge.DemandeCongeService;
import com.hrapp.rh.conge.SoldeCongeService;
import com.hrapp.rh.conge.StatutDemandeConge;
import com.hrapp.rh.departement.DepartementService;
import com.hrapp.rh.employe.EmployeService;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import com.hrapp.rh.tableaudebord.TableauDeBordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outils de l'assistant RH, en lecture seule. Partagés par le chat (API Claude) et le serveur MCP.
 *
 * Sécurité : chaque outil appelle le même service métier que l'écran correspondant, avec l'utilisateur
 * connecté. Les règles d'accès (périmètre du manager, salaire masqué...) sont donc exactement celles
 * de l'application, sans aucune logique dupliquée. Chaque appel est enregistré dans le journal d'audit.
 */
@Slf4j
@Service
public class OutilsRhService {

    /** D'où vient l'appel (enregistré dans le journal d'audit). */
    public enum Source { CHAT, MCP }

    static final String ENTITE_AUDIT = "AssistantRH";
    private static final int MAX_RESULTATS = 20;
    private static final int MAX_ABSENCES = 50;
    private static final long MAX_JOURS_PERIODE = 366;

    private static final List<OutilRh> OUTILS = List.of(
            new OutilRh("lister_departements",
                    "Liste des départements de l'entreprise avec leur identifiant et leur nombre d'employés. "
                            + "À utiliser pour retrouver l'identifiant d'un département cité par son nom (ex. « IT » = Informatique).",
                    Map.of(), List.of()),
            new OutilRh("rechercher_employes",
                    "Recherche d'employés par nom, prénom ou email, éventuellement dans un département. "
                            + "Renvoie au plus 20 résultats.",
                    Map.of("recherche", OutilRh.texte("Texte recherché dans le nom, le prénom ou l'email (facultatif)"),
                            "departementId", OutilRh.entier("Identifiant du département. Facultatif : ne pas le fournir pour toute l'entreprise")),
                    List.of()),
            new OutilRh("consulter_employe",
                    "Fiche détaillée d'un employé : département, rôle, manager, date d'embauche, salaire "
                            + "(vide s'il n'est pas communiqué à l'utilisateur).",
                    Map.of("employeId", OutilRh.entier("Identifiant de l'employé")),
                    List.of("employeId")),
            new OutilRh("statistiques_departement",
                    "Statistiques RH : effectif (nombreEmployes), nombre de demandes de congé en attente "
                            + "(demandesEnAttente, 0 = aucune), embauches du mois, absents du jour, "
                            + "embauches par mois, répartition des congés, masse salariale par département "
                            + "(vide si elle n'est pas communiquée à l'utilisateur). Sans département : toute l'entreprise.",
                    Map.of("departementId", OutilRh.entier("Identifiant du département. Facultatif : ne pas le fournir pour toute l'entreprise"),
                            "annee", OutilRh.entier("Année des graphiques (facultatif, année en cours par défaut)")),
                    List.of()),
            new OutilRh("solde_conges",
                    "Soldes de congés (alloués, pris, en attente, disponibles) d'un employé pour une année. "
                            + "Sans employé : les soldes de l'utilisateur connecté.",
                    Map.of("employeId", OutilRh.entier("Identifiant de l'employé. Facultatif : ne pas le fournir pour l'utilisateur connecté"),
                            "annee", OutilRh.entier("Année (facultatif, année en cours par défaut)")),
                    List.of()),
            new OutilRh("demandes_en_attente",
                    "Demandes de congé en attente de décision (celles que l'utilisateur connecté peut traiter ou suivre).",
                    Map.of(), List.of()),
            new OutilRh("absences_periode",
                    "Employés en congé approuvé sur une période (chaque congé qui chevauche la période). "
                            + "Pour « la semaine prochaine », calculer les dates du lundi au vendredi à partir de la date du jour.",
                    Map.of("du", OutilRh.date("Premier jour de la période"),
                            "au", OutilRh.date("Dernier jour de la période"),
                            "departementId", OutilRh.entier("Identifiant du département. Facultatif : ne pas le fournir pour toute l'entreprise")),
                    List.of("du", "au")));

    private final EmployeService employeService;
    private final DepartementService departementService;
    private final TableauDeBordService tableauDeBordService;
    private final SoldeCongeService soldeService;
    private final DemandeCongeService demandeService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public OutilsRhService(EmployeService employeService, DepartementService departementService,
                           TableauDeBordService tableauDeBordService, SoldeCongeService soldeService,
                           DemandeCongeService demandeService, AuditService auditService,
                           ObjectMapper objectMapper) {
        this.employeService = employeService;
        this.departementService = departementService;
        this.tableauDeBordService = tableauDeBordService;
        this.soldeService = soldeService;
        this.demandeService = demandeService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<OutilRh> definitions() {
        return OUTILS;
    }

    /** Exécute un outil avec les droits de cet utilisateur ; ne lève jamais d'exception métier. */
    public ResultatOutil execute(UtilisateurConnecte utilisateur, String nom, Map<String, Object> arguments,
                                 Source source) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        ResultatOutil resultat;
        try {
            resultat = ResultatOutil.succes(toJson(run(utilisateur, nom, args)));
        } catch (AccessDeniedException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            resultat = ResultatOutil.echec(message.startsWith("Accès refusé") ? message : "Accès refusé : " + message);
        } catch (RessourceIntrouvableException | RequeteInvalideException | ConflitException e) {
            resultat = ResultatOutil.echec(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Erreur de l'outil {} de l'assistant", nom, e);
            resultat = ResultatOutil.echec("Erreur interne pendant l'exécution de l'outil");
        }
        audit(utilisateur, nom, args, source, resultat);
        return resultat;
    }

    // ---------- outils ----------

    private Object run(UtilisateurConnecte u, String nom, Map<String, Object> args) {
        return switch (nom) {
            case "lister_departements" -> departementService.findAll();
            case "rechercher_employes" -> employeService.search(u, text(args, "recherche"),
                    optionalId(args, "departementId"), null,
                    PageRequest.of(0, MAX_RESULTATS, Sort.by("nom", "prenom")));
            case "consulter_employe" -> employeService.findById(u, required(optionalId(args, "employeId"), "employeId"));
            case "statistiques_departement" -> tableauDeBordService.build(u, integer(args, "annee"),
                    optionalId(args, "departementId"));
            case "solde_conges" -> leaveBalance(u, optionalId(args, "employeId"), integer(args, "annee"));
            case "demandes_en_attente" -> pendingRequests(u);
            case "absences_periode" -> absences(u, required(date(args, "du"), "du"), required(date(args, "au"), "au"),
                    optionalId(args, "departementId"));
            default -> throw new RequeteInvalideException("Outil inconnu : " + nom);
        };
    }

    private Object leaveBalance(UtilisateurConnecte u, Long employeId, Integer annee) {
        return employeId == null || employeId.equals(u.getEmployeId())
                ? soldeService.findMine(u, annee)
                : soldeService.findForEmployee(u, employeId, annee);
    }

    private Object pendingRequests(UtilisateurConnecte u) {
        if (u.isAdminOrRh()) {
            return demandeService.listAll(StatutDemandeConge.EN_ATTENTE, null, null, null, null, 0, MAX_RESULTATS);
        }
        if (u.hasRole(Roles.MANAGER)) {
            return demandeService.listTeam(u, StatutDemandeConge.EN_ATTENTE, null, 0, MAX_RESULTATS);
        }
        return demandeService.listMine(u, StatutDemandeConge.EN_ATTENTE, null, 0, MAX_RESULTATS);
    }

    /**
     * Périmètre, comme pour le tableau de bord : ADMIN/RH toute l'entreprise, MANAGER son département,
     * EMPLOYE uniquement ses propres congés (il ne voit pas les absences de ses collègues).
     */
    private Object absences(UtilisateurConnecte u, LocalDate du, LocalDate au, Long departementId) {
        if (au.isBefore(du)) {
            throw new RequeteInvalideException("La date de fin précède la date de début");
        }
        if (ChronoUnit.DAYS.between(du, au) > MAX_JOURS_PERIODE) {
            throw new RequeteInvalideException("La période ne peut pas dépasser 366 jours");
        }
        Long employeId = null;
        Long departement = departementId;
        if (!u.isAdminOrRh()) {
            if (u.hasRole(Roles.MANAGER)) {
                if (departementId != null && !departementId.equals(u.getDepartementId())) {
                    throw new AccessDeniedException("un manager ne voit que les absences de son département");
                }
                departement = u.getDepartementId();
            } else {
                employeId = u.getEmployeId();
                departement = null;
            }
        }
        return demandeService.listAll(StatutDemandeConge.APPROUVEE, employeId, departement, du, au, 0, MAX_ABSENCES);
    }

    // ---------- lecture des arguments (le modèle ou le client MCP peut se tromper de type) ----------

    private static String text(Map<String, Object> args, String nom) {
        Object valeur = args.get(nom);
        return valeur == null || valeur.toString().isBlank() ? null : valeur.toString().trim();
    }

    private static Long number(Map<String, Object> args, String nom) {
        Object valeur = args.get(nom);
        if (valeur == null || valeur.toString().isBlank()) {
            return null;
        }
        if (valeur instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(valeur.toString().trim());
        } catch (NumberFormatException e) {
            throw new RequeteInvalideException("Le paramètre « " + nom + " » doit être un nombre entier");
        }
    }

    /**
     * Identifiant facultatif : 0 ou moins veut dire « non précisé ». Les modèles de langage envoient souvent
     * departementId = 0 pour « toute l'entreprise » ; sans cette règle, l'outil répondrait « département introuvable ».
     */
    private static Long optionalId(Map<String, Object> args, String nom) {
        Long valeur = number(args, nom);
        return valeur == null || valeur <= 0 ? null : valeur;
    }

    private static Integer integer(Map<String, Object> args, String nom) {
        Long valeur = number(args, nom);
        return valeur == null ? null : Math.toIntExact(valeur);
    }

    private static LocalDate date(Map<String, Object> args, String nom) {
        String valeur = text(args, nom);
        if (valeur == null) {
            return null;
        }
        try {
            return LocalDate.parse(valeur);
        } catch (DateTimeParseException e) {
            throw new RequeteInvalideException("Le paramètre « " + nom + " » doit être une date AAAA-MM-JJ");
        }
    }

    private static <T> T required(T valeur, String nom) {
        if (valeur == null) {
            throw new RequeteInvalideException("Le paramètre « " + nom + " » est obligatoire");
        }
        return valeur;
    }

    // ---------- méthodes privées ----------

    private String toJson(Object valeur) {
        try {
            return objectMapper.writeValueAsString(valeur);
        } catch (JacksonException e) {
            throw new IllegalStateException("Sérialisation du résultat impossible", e);
        }
    }

    private void audit(UtilisateurConnecte u, String nom, Map<String, Object> args, Source source,
                       ResultatOutil resultat) {
        Map<String, Object> resume = new LinkedHashMap<>();
        resume.put("outil", nom);
        resume.put("source", source.name());
        resume.put("arguments", args);
        if (resultat.erreur()) {
            resume.put("erreur", resultat.contenu());
        }
        String details;
        try {
            details = objectMapper.writeValueAsString(resume);
        } catch (JacksonException e) {
            details = "outil=" + nom + " source=" + source;
        }
        auditService.recordAs(u.getUtilisateurId(), u.getUsername(), AuditAction.APPEL_ASSISTANT,
                ENTITE_AUDIT, null, details);
    }
}
