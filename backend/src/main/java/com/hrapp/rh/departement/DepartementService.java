package com.hrapp.rh.departement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartementService {

    private final DepartementRepository repository;

    /** Retourne tous les départements. */
    @Transactional(readOnly = true)
    public List<DepartementResponse> trouverTous() {
        return repository.findAll().stream()
                .map(d -> new DepartementResponse(d.getId(), d.getNom(), d.getDescription()))
                .toList();
    }
}