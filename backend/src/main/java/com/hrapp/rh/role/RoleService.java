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
    public List<RoleResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse findById(Long id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (repository.existsByNomIgnoreCase(request.nom())) {
            throw new ConflitException("Un rÃ´le nommÃ© Â« " + request.nom() + " Â» existe dÃ©jÃ ");
        }
        Role role = repository.save(mapper.toEntity(request));
        return mapper.toResponse(role);
    }

    @Transactional
    public RoleResponse update(Long id, RoleRequest request) {
        Role role = findOrThrow(id);
        if (repository.existsByNomIgnoreCaseAndIdNot(request.nom(), id)) {
            throw new ConflitException("Un rÃ´le nommÃ© Â« " + request.nom() + " Â» existe dÃ©jÃ ");
        }
        // pas besoin de save() : l'entitÃ© est Â« suivie Â» dans la transaction,
        // Hibernate enregistre les changements tout seul Ã  la fin
        mapper.updateEntity(request, role);
        return mapper.toResponse(role);
    }

    @Transactional
    public void delete(Long id) {
        Role role = findOrThrow(id);
        if (employeRepository.existsByRoleId(id)) {
            throw new ConflitException("Suppression impossible : des employÃ©s utilisent ce rÃ´le");
        }
        repository.delete(role);
    }

    private Role findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("RÃ´le introuvable (id=" + id + ")"));
    }
}
