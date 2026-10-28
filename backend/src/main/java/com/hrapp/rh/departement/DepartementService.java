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
    public List<DepartementResponse> findAll() {
        return repository.findAllWithEmployeeCount();
    }

    @Transactional(readOnly = true)
    public DepartementDetailResponse findById(Long id) {
        Departement d = findOrThrow(id);
        List<EmployeResume> employes = employeRepository.findByDepartementId(id).stream()
                .map(e -> new EmployeResume(e.getId(), e.getNom(), e.getPrenom(), e.getEmail()))
                .toList();
        return new DepartementDetailResponse(d.getId(), d.getNom(), d.getDescription(), employes);
    }

    @Transactional
    public DepartementResponse create(DepartementRequest request) {
        if (repository.existsByNomIgnoreCase(request.nom())) {
            throw new ConflitException("Un département nommé « " + request.nom() + " » existe déjà");
        }
        Departement d = repository.save(mapper.toEntity(request));
        return new DepartementResponse(d.getId(), d.getNom(), d.getDescription(), 0L);
    }

    @Transactional
    public DepartementResponse update(Long id, DepartementRequest request) {
        Departement d = findOrThrow(id);
        if (repository.existsByNomIgnoreCaseAndIdNot(request.nom(), id)) {
            throw new ConflitException("Un département nommé « " + request.nom() + " » existe déjà");
        }
        mapper.updateEntity(request, d);
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

    private Departement findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Département introuvable (id=" + id + ")"));
    }
}
