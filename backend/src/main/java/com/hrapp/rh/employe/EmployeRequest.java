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

        @NotBlank(message = "Le téléphone est obligatoire")
        @Size(max = 30, message = "Le téléphone ne doit pas dépasser 30 caractères")
        @Pattern(regexp = "^\\s*\\+?[0-9][0-9 .()\\-]{6,24}[0-9]\\s*$",
                message = "Le téléphone n'est pas valide (ex. +216 22 345 678)")
        String telephone,

        @NotNull(message = "La date d'embauche est obligatoire")
        LocalDate dateEmbauche,

        @PositiveOrZero(message = "Le salaire doit être positif")
        @Digits(integer = 8, fraction = 3, message = "Salaire invalide (8 chiffres max, 3 décimales : millimes)")
        BigDecimal salaire,

        @NotNull(message = "Le département est obligatoire")
        Long departementId,

        @NotNull(message = "Le rôle est obligatoire")
        Long roleId,

        Long managerId        // optionnel
) {}