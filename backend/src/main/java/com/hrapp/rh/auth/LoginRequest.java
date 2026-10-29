package com.hrapp.rh.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Le nom d'utilisateur est obligatoire") String nomUtilisateur,
        @NotBlank(message = "Le mot de passe est obligatoire") String motDePasse
) {}