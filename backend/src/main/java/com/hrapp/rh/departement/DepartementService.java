package com.hrapp.rh.departement;

import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.employe.EmployeRepository;
import com.hrapp.rh.employe.EmployeResume;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartementService {

    private final DepartementRepository repository;
    private final EmployeRepository employeRepository;
    private final DepartementMapper mapper;

    @Transactional(readOnly = true)
    public List<DepartementResponse> findAll() {
        return repository.findAllWithEmployeeCount();
    }

    /** Détail d'un département ; la liste des employés dépend de qui demande. */
    @Transactional(readOnly = true)
    public DepartementDetailResponse findById(UtilisateurConnecte utilisateur, Long id) {
        Departement d = findOrThrow(id);
        List<EmployeResume> employes = canSeeEmployees(utilisateur, id)
                ? employeRepository.findByDepartementId(id).stream()
                .map(e -> new EmployeResume(e.getId(), e.getNom(), e.getPrenom(), e.getEmail()))
                .toList()
                : List.of();
        return new DepartementDetailResponse(d.getId(), d.getNom(), d.getDescription(), employes);
    }

    @Transactional
    public DepartementResponse create(DepartementRequest requete) {
        if (repository.existsByNomIgnoreCase(requete.nom())) {
            throw new ConflitException("Un département nommé « " + requete.nom() + " » existe déjà");
        }
        Departement d = repository.save(mapper.toEntity(requete));
        return new DepartementResponse(d.getId(), d.getNom(), d.getDescription(), 0L);
    }

    @Transactional
    public DepartementResponse update(Long id, DepartementRequest requete) {
        Departement d = findOrThrow(id);
        if (repository.existsByNomIgnoreCaseAndIdNot(requete.nom(), id)) {
            throw new ConflitException("Un département nommé « " + requete.nom() + " » existe déjà");
        }
        mapper.updateEntity(requete, d);
        return new DepartementResponse(d.getId(), d.getNom(), d.getDescription(),
                employeRepository.countByDepartementId(id));
    }

    @Transactional
    public void delete(Long id) {
        Departement d = findOrThrow(id);
        if (employeRepository.countByDepartementId(id) > 0) {
            throw new ConflitException("Suppression impossible : ce département contient encore des employés");
        }
        repository.delete(d);
    }

    // ---------- méthodes privées ----------

    /** ADMIN/RH : tous les départements ; MANAGER : le sien ; les autres : aucune liste. */
    private boolean canSeeEmployees(UtilisateurConnecte utilisateur, Long departementId) {
        if (utilisateur.isAdminOrRh()) {
            return true;
        }
        return utilisateur.hasRole(Roles.MANAGER) && departementId.equals(utilisateur.getDepartementId());
    }

    private Departement findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Département introuvable (id=" + id + ")"));
    }
}