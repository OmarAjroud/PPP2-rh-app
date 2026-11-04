package com.hrapp.rh.conge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefusRequest(
        @NotBlank(message = "Le motif du refus est obligatoire")
        @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères")
        String motif
) {}