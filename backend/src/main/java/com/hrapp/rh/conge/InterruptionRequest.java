package com.hrapp.rh.conge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InterruptionRequest(
        @NotBlank(message = "Le motif de l'interruption est obligatoire")
        @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères")
        String motif
) {}
