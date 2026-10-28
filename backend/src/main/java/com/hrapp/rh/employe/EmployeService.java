package com.hrapp.rh.employe;

import com.hrapp.rh.common.dto.PageReponse;
import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.departement.Departement;
import com.hrapp.rh.departement.DepartementRepository;
import com.hrapp.rh.role.Role;
import com.hrapp.rh.role.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    private final EmployeMapper mapper;

    @Transactional(readOnly = true)
    public PageReponse<EmployeResponse> lister(String recherche, Long departementId,
                                               Long roleId, Pageable pageable) {
        pageable.getSort().forEach(ordre -> {
            if (!TRIS_AUTORISES.contains(ordre.getProperty())) {
                throw new RequeteInvalideException("Tri non autorisé sur : " + ordre.getProperty()
                        + ". Valeurs possibles : " + TRIS_AUTORISES);
            }
        });

        Specification<Employe> filtre = EmployeSpecification.recherche(recherche)
                .and(EmployeSpecification.departement(departementId))
                .and(EmployeSpecification.role(roleId));

        return PageReponse.de(repository.findAll(filtre, pageable).map(mapper::versReponse));
    }

    @Transactional(readOnly = true)
    public EmployeDetailResponse trouverParId(Long id) {
        return mapper.versDetail(chargerOuErreur(id));
    }

    @Transactional
    public EmployeDetailResponse creer(EmployeRequest requete) {
        if (repository.existsByEmailIgnoreCase(requete.email())) {
            throw new ConflitException("Un employé avec l'email « " + requete.email() + " » existe déjà");
        }
        Employe employe = mapper.versEntite(requete);
        appliquerRelations(employe, requete);
        return mapper.versDetail(repository.save(employe));
    }

    @Transactional
    public EmployeDetailResponse modifier(Long id, EmployeRequest requete) {
        Employe employe = chargerOuErreur(id);
        if (repository.existsByEmailIgnoreCaseAndIdNot(requete.email(), id)) {
            throw new ConflitException("Un employé avec l'email « " + requete.email() + " » existe déjà");
        }
        mapper.mettreAJour(requete, employe);
        appliquerRelations(employe, requete);
        return mapper.versDetail(employe);
    }

    @Transactional
    public void supprimer(Long id) {
        Employe employe = chargerOuErreur(id);
        if (repository.existsByManagerId(id)) {
            throw new ConflitException("Suppression impossible : cet employé est le manager d'autres employés");
        }
        repository.delete(employe);
    }

    // ---------- méthodes privées ----------

    /** Charge département, rôle et manager depuis la base et les attache à l'employé. */
    private void appliquerRelations(Employe employe, EmployeRequest requete) {
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

    private Employe chargerOuErreur(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Employé introuvable (id=" + id + ")"));
    }
}