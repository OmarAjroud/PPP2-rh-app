package com.hrapp.rh.employe;

import com.hrapp.rh.common.dto.PageReponse;
import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.departement.Departement;
import com.hrapp.rh.departement.DepartementRepository;
import com.hrapp.rh.role.Role;
import com.hrapp.rh.role.RoleRepository;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import com.hrapp.rh.utilisateur.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class EmployeService {

    /** Colonnes sur lesquelles le tri est autorisé (évite les erreurs 500 sur un tri inconnu). */
    private static final Set<String> TRIS_AUTORISES =
            Set.of("id", "nom", "prenom", "email", "dateEmbauche", "departement.nom", "role.nom");

    private final EmployeRepository repository;
    private final DepartementRepository departementRepository;
    private final RoleRepository roleRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EmployeMapper mapper;

    @Transactional(readOnly = true)
    public PageReponse<EmployeResponse> search(UtilisateurConnecte utilisateur, String recherche,
                                               Long departementId, Long roleId, Pageable pageable) {
        pageable.getSort().forEach(ordre -> {
            if (!TRIS_AUTORISES.contains(ordre.getProperty())) {
                throw new RequeteInvalideException("Tri non autorisé sur : " + ordre.getProperty()
                        + ". Valeurs possibles : " + TRIS_AUTORISES);
            }
        });

        Specification<Employe> filtre = EmployeSpecification.search(recherche)
                .and(EmployeSpecification.inDepartement(departementId))
                .and(EmployeSpecification.hasRole(roleId));

        // Refus par défaut : seuls ADMIN et RH voient tout le monde
        if (utilisateur.hasRole(Roles.MANAGER)) {
            // son département, plus son équipe directe (qui peut être dans un autre département)
            filtre = filtre.and(EmployeSpecification.inDepartement(utilisateur.getDepartementId())
                    .or(EmployeSpecification.managedBy(utilisateur.getEmployeId())));
        } else if (!utilisateur.isAdminOrRh()) {
            filtre = filtre.and(EmployeSpecification.hasId(utilisateur.getEmployeId()));
        }

        return PageReponse.from(repository.findAll(filtre, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public EmployeDetailResponse findById(UtilisateurConnecte utilisateur, Long id) {
        Employe employe = findOrThrow(id);
        verifyCanView(utilisateur, employe);

        EmployeDetailResponse detail = mapper.toDetail(employe);
        return canSeeSalary(utilisateur, employe) ? detail : detail.withoutSalary();
    }

    @Transactional
    public EmployeDetailResponse create(UtilisateurConnecte acteur, EmployeRequest requete) {
        if (repository.existsByEmailIgnoreCase(requete.email())) {
            throw new ConflitException("Un employé avec l'email « " + requete.email() + " » existe déjà");
        }
        Employe employe = mapper.toEntity(requete);
        applyRelations(employe, requete);
        verifyCanManage(acteur, employe.getRole());          // le rôle demandé
        return mapper.toDetail(repository.save(employe));
    }

    @Transactional
    public EmployeDetailResponse update(UtilisateurConnecte acteur, Long id, EmployeRequest requete) {
        Employe employe = findOrThrow(id);
        verifyCanManage(acteur, employe.getRole());          // la cible actuelle (est-ce un ADMIN ?)
        // Un RH ne modifie pas sa propre fiche (salaire, rôle, manager...) : un autre RH ou un ADMIN le fait
        if (id.equals(acteur.getEmployeId()) && !acteur.hasRole(Roles.ADMIN)) {
            throw new AccessDeniedException("Vous ne pouvez pas modifier votre propre fiche : "
                    + "demandez à un autre RH ou à un administrateur");
        }
        if (repository.existsByEmailIgnoreCaseAndIdNot(requete.email(), id)) {
            throw new ConflitException("Un employé avec l'email « " + requete.email() + " » existe déjà");
        }
        boolean etaitAdmin = Roles.ADMIN.equals(employe.getRole().getNom());
        mapper.updateEntity(requete, employe);
        applyRelations(employe, requete);
        verifyCanManage(acteur, employe.getRole());          // le nouveau rôle demandé
        if (etaitAdmin && !Roles.ADMIN.equals(employe.getRole().getNom())) {
            verifyNotLastAdmin();
        }
        return mapper.toDetail(employe);
    }

    @Transactional
    public void delete(UtilisateurConnecte acteur, Long id) {
        Employe employe = findOrThrow(id);
        verifyCanManage(acteur, employe.getRole());
        if (id.equals(acteur.getEmployeId())) {
            throw new AccessDeniedException("Vous ne pouvez pas supprimer votre propre fiche");
        }
        if (repository.existsByManagerId(id)) {
            throw new ConflitException("Suppression impossible : cet employé est le manager d'autres employés");
        }
        if (Roles.ADMIN.equals(employe.getRole().getNom())) {
            verifyNotLastAdmin();
        }
        try {
            utilisateurRepository.deleteByEmployeId(id);     // supprime d'abord son compte de connexion
            repository.delete(employe);
            repository.flush();                              // les contraintes sont vérifiées ici, pas au commit
        } catch (DataIntegrityViolationException e) {
            // demandes de congé, soldes, décisions... l'historique doit être conservé
            throw new ConflitException("Suppression impossible : cet employé a un historique (congés, soldes, décisions). "
                    + "Pour un départ, désactivez plutôt son compte : POST /api/employes/" + id + "/compte/desactivation");
        }
    }

    // ---------- règles d'accès ----------

    /** ADMIN/RH : tout le monde ; MANAGER : son département ; EMPLOYE : lui-même. */
    private void verifyCanView(UtilisateurConnecte utilisateur, Employe employe) {
        if (utilisateur.isAdminOrRh()) {
            return;
        }
        if (utilisateur.hasRole(Roles.MANAGER)
                && (employe.getDepartement().getId().equals(utilisateur.getDepartementId())
                    || isDirectReport(utilisateur, employe))) {
            return;      // son département, et son équipe directe (dont il valide les congés)
        }
        if (employe.getId().equals(utilisateur.getEmployeId())) {
            return;
        }
        throw new AccessDeniedException("Accès refusé à cet employé");
    }

    /** Salaire visible pour ADMIN/RH, et pour chacun sur sa propre fiche. */
    private boolean canSeeSalary(UtilisateurConnecte utilisateur, Employe employe) {
        return utilisateur.isAdminOrRh() || employe.getId().equals(utilisateur.getEmployeId());
    }

    /** Seul un ADMIN peut créer, modifier ou supprimer un ADMIN, ou attribuer le rôle ADMIN. */
    private void verifyCanManage(UtilisateurConnecte acteur, Role role) {
        if (Roles.ADMIN.equals(role.getNom()) && !acteur.hasRole(Roles.ADMIN)) {
            throw new AccessDeniedException("Seul un administrateur peut gérer un compte ADMIN");
        }
    }

    /** Le manager est-il directement responsable de cet employé ? */
    private static boolean isDirectReport(UtilisateurConnecte manager, Employe employe) {
        return employe.getManager() != null && employe.getManager().getId().equals(manager.getEmployeId());
    }

    /** Il doit toujours rester au moins un ADMIN (gestion des rôles, journal d'audit). */
    private void verifyNotLastAdmin() {
        if (repository.countByRoleNom(Roles.ADMIN) <= 1) {
            throw new ConflitException("Impossible : c'est le dernier administrateur. Nommez d'abord un autre ADMIN");
        }
    }

    /** A manager de B et B manager de A (même indirectement) : refusé, la hiérarchie doit rester un arbre. */
    private static void verifyNoManagementLoop(Employe employe, Employe nouveauManager) {
        if (employe.getId() == null) {
            return;                                           // création : personne ne dépend encore de lui
        }
        Employe courant = nouveauManager;
        for (int profondeur = 0; courant != null && profondeur < 100; profondeur++) {
            if (employe.getId().equals(courant.getId())) {
                throw new ConflitException("Hiérarchie impossible : " + nouveauManager.getPrenom() + " "
                        + nouveauManager.getNom() + " dépend déjà (directement ou non) de cet employé");
            }
            courant = courant.getManager();
        }
    }

    // ---------- méthodes privées ----------

    /** Charge département, rôle et manager depuis la base et les attache à l'employé. */
    private void applyRelations(Employe employe, EmployeRequest requete) {
        Departement departement = departementRepository.findById(requete.departementId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Département introuvable (id=" + requete.departementId() + ")"));
        Role role = roleRepository.findById(requete.roleId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Rôle introuvable (id=" + requete.roleId() + ")"));

        Employe manager = null;
        if (requete.managerId() != null) {
            if (requete.managerId().equals(employe.getId())) {
                throw new ConflitException("Un employé ne peut pas être son propre manager");
            }
            manager = repository.findById(requete.managerId())
                    .orElseThrow(() -> new RessourceIntrouvableException(
                            "Manager introuvable (id=" + requete.managerId() + ")"));
            verifyNoManagementLoop(employe, manager);
        }

        employe.setDepartement(departement);
        employe.setRole(role);
        employe.setManager(manager);
    }

    private Employe findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Employé introuvable (id=" + id + ")"));
    }
}