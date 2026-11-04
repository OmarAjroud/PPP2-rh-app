package com.hrapp.rh.conge;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Ajustement du solde alloué : jours positifs pour en ajouter, négatifs pour en retirer. */
public record AjustementSoldeRequest(
        @NotNull(message = "Le type de congé est obligatoire")
        Long typeCongeId,

        @NotNull(message = "L'année est obligatoire")
        Integer annee,

        @NotNull(message = "Le nombre de jours est obligatoire")
        @Digits(integer = 3, fraction = 1, message = "Nombre de jours invalide (3 chiffres max, 1 décimale)")
        BigDecimal jours,

        @NotBlank(message = "Le commentaire est obligatoire")
        @Size(max = 255, message = "Le commentaire ne doit pas dépasser 255 caractères")
        String commentaire
) {}