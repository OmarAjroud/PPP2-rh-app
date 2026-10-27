package com.hrapp.rh.departement;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departements")
@RequiredArgsConstructor
public class DepartementController {

    private final DepartementService service;

    @GetMapping
    public List<DepartementResponse> lister() {
        return service.trouverTous();
    }
}