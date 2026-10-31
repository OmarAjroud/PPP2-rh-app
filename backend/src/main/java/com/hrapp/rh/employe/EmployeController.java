package com.hrapp.rh.employe;

import com.hrapp.rh.common.dto.PageReponse;
import com.hrapp.rh.securite.UtilisateurConnecte;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/api/employes")
@RequiredArgsConstructor
public class EmployeController {

    private final EmployeService service;

    // Tout utilisateur connecté : le service restreint les résultats selon son rôle
    @GetMapping
    public PageReponse<EmployeResponse> list(
            @AuthenticationPrincipal UtilisateurConnecte utilisateur,
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) Long departementId,
            @RequestParam(required = false) Long roleId,
            @ParameterObject @PageableDefault(size = 10, sort = "nom") Pageable pageable) {
        return service.search(utilisateur, recherche, departementId, roleId, pageable);
    }

    @GetMapping("/{id}")
    public EmployeDetailResponse getById(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                         @PathVariable Long id) {
        return service.findById(utilisateur, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public EmployeDetailResponse create(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                        @Valid @RequestBody EmployeRequest requete) {
        return service.create(utilisateur, requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public EmployeDetailResponse update(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                        @PathVariable Long id,
                                        @Valid @RequestBody EmployeRequest requete) {
        return service.update(utilisateur, id, requete);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public void delete(@AuthenticationPrincipal UtilisateurConnecte utilisateur, @PathVariable Long id) {
        service.delete(utilisateur, id);
    }
}