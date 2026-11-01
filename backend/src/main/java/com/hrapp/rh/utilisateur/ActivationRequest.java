package com.hrapp.rh.utilisateur;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ActivationRequest(
        @NotBlank(message = "Le jeton est obligatoire")
        String jeton,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Pattern(regexp = RegleMotDePasse.REGEX, message = RegleMotDePasse.MESSAGE)
        String motDePasse
) {}