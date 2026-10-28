package com.hrapp.rh.employe;

import com.hrapp.rh.common.dto.PageReponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employes")
@RequiredArgsConstructor
public class EmployeController {

    private final EmployeService service;

    /**
     * Exemples :
     *   GET /api/employes?page=0&size=10&sort=nom,asc
     *   GET /api/employes?recherche=gharbi
     *   GET /api/employes?departementId=1&sort=departement.nom,desc
     */
    @GetMapping
    public PageReponse<EmployeResponse> lister(
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) Long departementId,
            @RequestParam(required = false) Long roleId,
            @PageableDefault(size = 10, sort = "nom") Pageable pageable) {
        return service.lister(recherche, departementId, roleId, pageable);
    }

    @GetMapping("/{id}")
    public EmployeDetailResponse details(@PathVariable Long id) {
        return service.trouverParId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeDetailResponse creer(@Valid @RequestBody EmployeRequest requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    public EmployeDetailResponse modifier(@PathVariable Long id,
                                          @Valid @RequestBody EmployeRequest requete) {
        return service.modifier(id, requete);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id) {
        service.supprimer(id);
    }
}