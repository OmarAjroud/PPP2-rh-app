package com.hrapp.rh.employe;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Organigramme de l'entreprise : chaque employé avec son manager, le front-end construit l'arbre.
 * Réservé aux ADMIN et RH (choix produit : outil de gestion ; le tableau des droits limite la liste des
 * employés d'un manager à son département et celle d'un employé à lui-même).
 */
@RestController
@RequestMapping("/api/organigramme")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','RH')")
public class OrganigrammeController {

    private final EmployeRepository repository;

    @GetMapping
    @Transactional(readOnly = true)
    public List<NoeudOrganigramme> organigramme() {
        return repository.findOrganigramme();
    }
}
