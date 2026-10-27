package com.hrapp.rh.departement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartementRequest(
        @NotBlank(message = "Le nom du département est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String nom,

        @Size(max = 255, message = "La description ne doit pas dépasser 255 caractères")
        String description
) {}