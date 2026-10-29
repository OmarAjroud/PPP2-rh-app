package com.hrapp.rh.employe;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeDetailResponse(
        Long id,
        String nom,
        String prenom,
        String email,
        LocalDate dateEmbauche,
        BigDecimal salaire,
        Long departementId,
        String departementNom,
        Long roleId,
        String roleNom,
        Long managerId,
        String managerNomComplet
) {
    /** Copie de cette réponse sans le salaire (profils non autorisés). */
    public EmployeDetailResponse withoutSalary() {
        return new EmployeDetailResponse(id, nom, prenom, email, dateEmbauche, null,
                departementId, departementNom, roleId, roleNom, managerId, managerNomComplet);
    }
}