package com.hrapp.rh.employe;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String nom,

        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
        String prenom,

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email n'est pas valide")
        @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
        String email,

        @NotNull(message = "La date d'embauche est obligatoire")
        LocalDate dateEmbauche,

        @PositiveOrZero(message = "Le salaire doit être positif")
        @Digits(integer = 8, fraction = 2, message = "Salaire invalide (8 chiffres max, 2 décimales)")
        BigDecimal salaire,

        @NotNull(message = "Le département est obligatoire")
        Long departementId,

        @NotNull(message = "Le rôle est obligatoire")
        Long roleId,

        Long managerId        // optionnel
) {}