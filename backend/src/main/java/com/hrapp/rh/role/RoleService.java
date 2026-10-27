package com.hrapp.rh.role;

import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.employe.EmployeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository repository;
    private final EmployeRepository employeRepository;
    private final RoleMapper mapper;

    @Transactional(readOnly = true)
    public List<RoleResponse> lister() {
        return repository.findAll().stream().map(mapper::versReponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse trouverParId(Long id) {
        return mapper.versReponse(chargerOuErreur(id));
    }

    @Transactional
    public RoleResponse creer(RoleRequest requete) {
        if (repository.existsByNomIgnoreCase(requete.nom())) {
            throw new ConflitException("Un rôle nommé « " + requete.nom() + " » existe déjà");
        }
        Role role = repository.save(mapper.versEntite(requete));
        return mapper.versReponse(role);
    }

    @Transactional
    public RoleResponse modifier(Long id, RoleRequest requete) {
        Role role = chargerOuErreur(id);
        if (repository.existsByNomIgnoreCaseAndIdNot(requete.nom(), id)) {
            throw new ConflitException("Un rôle nommé « " + requete.nom() + " » existe déjà");
        }
        // pas besoin de save() : l'entité est « suivie » dans la transaction,
        // Hibernate enregistre les changements tout seul à la fin
        mapper.mettreAJour(requete, role);
        return mapper.versReponse(role);
    }

    @Transactional
    public void supprimer(Long id) {
        Role role = chargerOuErreur(id);
        if (employeRepository.existsByRoleId(id)) {
            throw new ConflitException("Suppression impossible : des employés utilisent ce rôle");
        }
        repository.delete(role);
    }

    private Role chargerOuErreur(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Rôle introuvable (id=" + id + ")"));
    }
}