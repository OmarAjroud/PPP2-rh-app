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
    public List<DepartementResponse> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public DepartementDetailResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepartementResponse create(@Valid @RequestBody DepartementRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public DepartementResponse update(@PathVariable Long id,
                                        @Valid @RequestBody DepartementRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
