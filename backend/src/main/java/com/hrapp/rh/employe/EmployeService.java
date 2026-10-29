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
            filtre = filtre.and(EmployeSpecification.inDepartement(utilisateur.getDepartementId()));
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
        if (repository.existsByEmailIgnoreCaseAndIdNot(requete.email(), id)) {
            throw new ConflitException("Un employé avec l'email « " + requete.email() + " » existe déjà");
        }
        mapper.updateEntity(requete, employe);
        applyRelations(employe, requete);
        verifyCanManage(acteur, employe.getRole());          // le nouveau rôle demandé
        return mapper.toDetail(employe);
    }

    @Transactional
    public void delete(UtilisateurConnecte acteur, Long id) {
        Employe employe = findOrThrow(id);
        verifyCanManage(acteur, employe.getRole());
        if (repository.existsByManagerId(id)) {
            throw new ConflitException("Suppression impossible : cet employé est le manager d'autres employés");
        }
        utilisateurRepository.deleteByEmployeId(id);         // supprime d'abord son compte de connexion
        repository.delete(employe);
    }

    // ---------- règles d'accès ----------

    /** ADMIN/RH : tout le monde ; MANAGER : son département ; EMPLOYE : lui-même. */
    private void verifyCanView(UtilisateurConnecte utilisateur, Employe employe) {
        if (utilisateur.isAdminOrRh()) {
            return;
        }
        if (utilisateur.hasRole(Roles.MANAGER)
                && employe.getDepartement().getId().equals(utilisateur.getDepartementId())) {
            return;
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