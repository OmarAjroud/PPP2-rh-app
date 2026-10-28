package com.hrapp.rh.employe;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeDetailResponse(
        Long id,
        String nom,
        String prenom,
        String email,
        LocalDate dateEmbauche,
        BigDecimal salaire,          // semaine 3 : masqué pour les profils non autorisés
        Long departementId,
        String departementNom,
        Long roleId,
        String roleNom,
        Long managerId,
        String managerNomComplet
) {}