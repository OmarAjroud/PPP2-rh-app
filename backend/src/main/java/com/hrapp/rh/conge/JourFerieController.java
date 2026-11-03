package com.hrapp.rh.conge;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jours-feries")
@RequiredArgsConstructor
public class JourFerieController {

    private final JourFerieService service;

    @GetMapping
    public List<JourFerieResponse> list(@RequestParam(required = false) Integer annee) {
        return service.findByYear(annee);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public JourFerieResponse create(@Valid @RequestBody JourFerieRequest requete) {
        return service.create(requete);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}