package com.hrapp.rh.conge;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/types-conge")
@RequiredArgsConstructor
public class TypeCongeController {

    private final TypeCongeService service;

    @GetMapping
    public List<TypeCongeResponse> list() {
        return service.findAll();
    }
}