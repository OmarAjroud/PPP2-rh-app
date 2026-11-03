package com.hrapp.rh.audit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.hrapp.rh.common.dto.PageReponse;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String ACTEUR_SYSTEME = "systeme";
    private static final int MAX_NOM = 100;
    private static final int MAX_DETAILS = 500;

    private final AuditRepository repository;
    private final ObjectMapper objectMapper;

    /** Enregistre un événement ; l'auteur est l'utilisateur connecté (ou « systeme »). */
    public void record(AuditAction action, String entite, Object entiteId, String details) {
        Acteur acteur = currentActor();
        write(acteur.utilisateurId(), acteur.nom(), action, entite, entiteId, null, null, details);
    }

    /** Enregistre un événement au nom d'un utilisateur précis (connexion, activation de compte...). */
    public void recordAs(Long utilisateurId, String nomUtilisateur, AuditAction action,
                         String entite, Object entiteId, String details) {
        write(utilisateurId, nomUtilisateur, action, entite, entiteId, null, null, details);
    }

    /** Enregistre un changement de données avec anciennes et nouvelles valeurs (utilisé par l'écouteur Hibernate). */
    public void recordChange(AuditAction action, String entite, Object entiteId,
                             Map<String, Object> anciennes, Map<String, Object> nouvelles) {
        Acteur acteur = currentActor();
        write(acteur.utilisateurId(), acteur.nom(), action, entite, entiteId,
                toJson(anciennes), toJson(nouvelles), null);
    }

    public PageReponse<AuditEntryResponse> search(AuditFilter filtre, int page, int taille) {
        if (filtre.du() != null && filtre.au() != null && filtre.du().isAfter(filtre.au())) {
            throw new RequeteInvalideException("La date de début doit précéder la date de fin");
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), 100));
        return PageReponse.from(repository.search(filtre, pageable));
    }

    public List<String> listEntities() {
        return repository.listEntities();
    }

    // ---------- méthodes privées ----------

    private record Acteur(Long utilisateurId, String nom) {}

    private Acteur currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UtilisateurConnecte utilisateur) {
            return new Acteur(utilisateur.getUtilisateurId(), utilisateur.getUsername());
        }
        return new Acteur(null, ACTEUR_SYSTEME);
    }

    private void write(Long utilisateurId, String nom, AuditAction action, String entite, Object entiteId,
                       String anciennes, String nouvelles, String details) {
        repository.insert(new AuditRecord(
                LocalDateTime.now(),
                utilisateurId,
                truncate(nom == null ? ACTEUR_SYSTEME : nom, MAX_NOM),
                action,
                entite,
                entiteId == null ? null : String.valueOf(entiteId),
                anciennes,
                nouvelles,
                truncate(details, MAX_DETAILS),
                currentIp()));
    }

    private String toJson(Map<String, Object> valeurs) {
        if (valeurs == null || valeurs.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(valeurs);
        } catch (JacksonException e) {
            throw new IllegalStateException("Sérialisation de l'audit impossible", e);
        }
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributs) {
            return attributs.getRequest().getRemoteAddr();
        }
        return null;                                          // hors requête HTTP (démarrage, tâche planifiée)
    }

    private static String truncate(String texte, int max) {
        return texte == null || texte.length() <= max ? texte : texte.substring(0, max);
    }
}
