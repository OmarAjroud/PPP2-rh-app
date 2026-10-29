package com.hrapp.rh.departement;

import com.hrapp.rh.securite.UtilisateurConnecte;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departements")
@RequiredArgsConstructor
public class DepartementController {

    private final DepartementService service;

    // Tout utilisateur connecté
    @GetMapping
    public List<DepartementResponse> list() {
        return service.findAll();
    }

    // Tout utilisateur connecté : le service décide si la liste des employés est visible
    @GetMapping("/{id}")
    public DepartementDetailResponse getById(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                             @PathVariable Long id) {
        return service.findById(utilisateur, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public DepartementResponse create(@Valid @RequestBody DepartementRequest requete) {
        return service.create(requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public DepartementResponse update(@PathVariable Long id, @Valid @RequestBody DepartementRequest requete) {
        return service.update(id, requete);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}