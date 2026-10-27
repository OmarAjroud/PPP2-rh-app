package com.hrapp.rh.departement;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departements")
@RequiredArgsConstructor
public class DepartementController {

    private final DepartementService service;

    @GetMapping
    public List<DepartementResponse> lister() {
        return service.lister();
    }

    @GetMapping("/{id}")
    public DepartementDetailResponse details(@PathVariable Long id) {
        return service.trouverParId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepartementResponse creer(@Valid @RequestBody DepartementRequest requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    public DepartementResponse modifier(@PathVariable Long id,
                                        @Valid @RequestBody DepartementRequest requete) {
        return service.modifier(id, requete);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id) {
        service.supprimer(id);
    }
}