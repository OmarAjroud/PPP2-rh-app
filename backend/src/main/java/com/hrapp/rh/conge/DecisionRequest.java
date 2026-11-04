package com.hrapp.rh.conge;

import jakarta.validation.constraints.Size;

public record DecisionRequest(
        @Size(max = 500, message = "Le commentaire ne doit pas dépasser 500 caractères")
        String motif
) {}