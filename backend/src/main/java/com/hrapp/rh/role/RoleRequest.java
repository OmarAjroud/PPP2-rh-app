package com.hrapp.rh.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleRequest(
        @NotBlank(message = "Le nom du rôle est obligatoire")
        @Size(max = 50, message = "Le nom ne doit pas dépasser 50 caractères")
        String nom,

        @Size(max = 255, message = "La description ne doit pas dépasser 255 caractères")
        String description
) {}