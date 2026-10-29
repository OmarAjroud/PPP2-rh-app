package com.hrapp.rh.role;

import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.employe.EmployeRepository;
import com.hrapp.rh.securite.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    /** Les 4 rôles utilisés par la sécurité : on ne peut ni les renommer ni les supprimer. */
    private static final Set<String> ROLES_SYSTEME =
            Set.of(Roles.ADMIN, Roles.RH, Roles.MANAGER, Roles.EMPLOYE);

    private final RoleRepository repository;
    private final EmployeRepository employeRepository;
    private final RoleMapper mapper;

    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse findById(Long id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public RoleResponse create(RoleRequest requete) {
        if (repository.existsByNomIgnoreCase(requete.nom())) {
            throw new ConflitException("Un rôle nommé « " + requete.nom() + " » existe déjà");
        }
        Role role = repository.save(mapper.toEntity(requete));
        return mapper.toResponse(role);
    }

    @Transactional
    public RoleResponse update(Long id, RoleRequest requete) {
        Role role = findOrThrow(id);
        verifyNotSystemRole(role);
        if (repository.existsByNomIgnoreCaseAndIdNot(requete.nom(), id)) {
            throw new ConflitException("Un rôle nommé « " + requete.nom() + " » existe déjà");
        }
        mapper.updateEntity(requete, role);
        return mapper.toResponse(role);
    }

    @Transactional
    public void delete(Long id) {
        Role role = findOrThrow(id);
        verifyNotSystemRole(role);
        if (employeRepository.existsByRoleId(id)) {
            throw new ConflitException("Suppression impossible : des employés utilisent ce rôle");
        }
        repository.delete(role);
    }

    // ---------- méthodes privées ----------

    private void verifyNotSystemRole(Role role) {
        if (ROLES_SYSTEME.contains(role.getNom())) {
            throw new ConflitException("Le rôle « " + role.getNom()
                    + " » est un rôle système : il ne peut être ni modifié ni supprimé");
        }
    }

    private Role findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Rôle introuvable (id=" + id + ")"));
    }
}