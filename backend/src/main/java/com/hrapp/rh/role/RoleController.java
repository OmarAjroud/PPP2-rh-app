package com.hrapp.rh.role;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService service;

    @GetMapping
    public List<RoleResponse> lister() {
        return service.lister();
    }

    @GetMapping("/{id}")
    public RoleResponse details(@PathVariable Long id) {
        return service.trouverParId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse creer(@Valid @RequestBody RoleRequest requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    public RoleResponse modifier(@PathVariable Long id, @Valid @RequestBody RoleRequest requete) {
        return service.modifier(id, requete);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id) {
        service.supprimer(id);
    }
}