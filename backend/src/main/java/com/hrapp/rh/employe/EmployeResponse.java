package com.hrapp.rh.employe;

public record EmployeResponse(
        Long id,
        String nom,
        String prenom,
        String email,
        Long departementId,
        String departementNom,
        Long roleId,
        String roleNom
) {}