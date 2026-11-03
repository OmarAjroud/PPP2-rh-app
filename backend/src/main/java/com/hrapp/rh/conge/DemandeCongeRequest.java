package com.hrapp.rh.conge;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DemandeCongeRequest(
        @NotNull(message = "Le type de congé est obligatoire")
        Long typeCongeId,

        @NotNull(message = "La date de début est obligatoire")
        LocalDate dateDebut,

        @NotNull(message = "La date de fin est obligatoire")
        LocalDate dateFin,

        @Size(max = 500, message = "Le commentaire ne doit pas dépasser 500 caractères")
        String commentaire
) {}