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

    /** Colonnes sur lesquelles le tri est autorisÃ© (Ã©vite les erreurs 500 sur un tri inconnu). */
    private static final Set<String> TRIS_AUTORISES =
            Set.of("id", "nom", "prenom", "email", "dateEmbauche", "departement.nom", "role.nom");

    private final EmployeRepository repository;
    private final DepartementRepository departementRepository;
    private final RoleRepository roleRepository;
    private final EmployeMapper mapper;

    @Transactional(readOnly = true)
    public PageReponse<EmployeResponse> search(String recherche, Long departementId,
                                                 Long roleId, Pageable pageable) {
        pageable.getSort().forEach(ordre -> {
            if (!TRIS_AUTORISES.contains(ordre.getProperty())) {
                throw new RequeteInvalideException("Tri non autorisé sur : " + ordre.getProperty()
                        + ". Valeurs possibles : " + TRIS_AUTORISES);
            }
        });

        Specification<Employe> filtre = EmployeSpecification.search(recherche)
                .and(EmployeSpecification.inDepartement(departementId))
                .and(EmployeSpecification.hasRole(roleId));

        return PageReponse.from(repository.findAll(filtre, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public EmployeDetailResponse findById(Long id) {
        return mapper.toDetail(findOrThrow(id));
    }

    @Transactional
    public EmployeDetailResponse create(EmployeRequest request) {
        if (repository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflitException("Un employé avec l'email « " + request.email() + " » existe déjà");
        }
        Employe employe = mapper.toEntity(request);
        applyRelations(employe, request);
        return mapper.toDetail(repository.save(employe));
    }

    @Transactional
    public EmployeDetailResponse update(Long id, EmployeRequest request) {
        Employe employe = findOrThrow(id);
        if (repository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new ConflitException("Un employé avec l'email « " + request.email() + " » existe déjà");
        }
        mapper.updateEntity(request, employe);
        applyRelations(employe, request);
        return mapper.toDetail(employe);
    }

    @Transactional
    public void delete(Long id) {
        Employe employe = findOrThrow(id);
        if (repository.existsByManagerId(id)) {
            throw new ConflitException("Suppression impossible : cet employé est le manager d'autres employés");
        }
        repository.delete(employe);
    }

    // ---------- mÃ©thodes privÃ©es ----------

    /** Charge dÃ©partement, rÃ´le et manager depuis la base et les attache Ã  l'employÃ©. */
    private void applyRelations(Employe employe, EmployeRequest request) {
        Departement departement = departementRepository.findById(request.departementId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Département introuvable (id=" + request.departementId() + ")"));
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Rôle introuvable (id=" + request.roleId() + ")"));

        Employe manager = null;
        if (request.managerId() != null) {
            if (request.managerId().equals(employe.getId())) {
                throw new ConflitException("Un employé ne peut pas être son propre manager");
            }
            manager = repository.findById(request.managerId())
                    .orElseThrow(() -> new RessourceIntrouvableException(
                            "Manager introuvable (id=" + request.managerId() + ")"));
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
