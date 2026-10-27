package com.hrapp.rh.departement;

import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.employe.EmployeRepository;
import com.hrapp.rh.employe.EmployeResume;
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
    public List<DepartementResponse> lister() {
        return repository.listerAvecNombreEmployes();
    }

    @Transactional(readOnly = true)
    public DepartementDetailResponse trouverParId(Long id) {
        Departement d = chargerOuErreur(id);
        List<EmployeResume> employes = employeRepository.findByDepartementId(id).stream()
                .map(e -> new EmployeResume(e.getId(), e.getNom(), e.getPrenom(), e.getEmail()))
                .toList();
        return new DepartementDetailResponse(d.getId(), d.getNom(), d.getDescription(), employes);
    }

    @Transactional
    public DepartementResponse creer(DepartementRequest requete) {
        if (repository.existsByNomIgnoreCase(requete.nom())) {
            throw new ConflitException("Un département nommé « " + requete.nom() + " » existe déjà");
        }
        Departement d = repository.save(mapper.versEntite(requete));
        return new DepartementResponse(d.getId(), d.getNom(), d.getDescription(), 0L);
    }

    @Transactional
    public DepartementResponse modifier(Long id, DepartementRequest requete) {
        Departement d = chargerOuErreur(id);
        if (repository.existsByNomIgnoreCaseAndIdNot(requete.nom(), id)) {
            throw new ConflitException("Un département nommé « " + requete.nom() + " » existe déjà");
        }
        mapper.mettreAJour(requete, d);
        return new DepartementResponse(d.getId(), d.getNom(), d.getDescription(),
                employeRepository.countByDepartementId(id));
    }

    @Transactional
    public void supprimer(Long id) {
        Departement d = chargerOuErreur(id);
        if (employeRepository.countByDepartementId(id) > 0) {
            throw new ConflitException("Suppression impossible : ce département contient encore des employés");
        }
        repository.delete(d);
    }

    private Departement chargerOuErreur(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Département introuvable (id=" + id + ")"));
    }
}