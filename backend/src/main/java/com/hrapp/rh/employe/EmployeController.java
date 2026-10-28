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
    public PageReponse<EmployeResponse> list(
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) Long departementId,
            @RequestParam(required = false) Long roleId,
            @PageableDefault(size = 10, sort = "nom") Pageable pageable) {
        return service.search(recherche, departementId, roleId, pageable);
    }

    @GetMapping("/{id}")
    public EmployeDetailResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeDetailResponse create(@Valid @RequestBody EmployeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public EmployeDetailResponse update(@PathVariable Long id,
                                          @Valid @RequestBody EmployeRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
