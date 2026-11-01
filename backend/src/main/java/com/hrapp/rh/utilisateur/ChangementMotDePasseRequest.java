package com.hrapp.rh.utilisateur;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangementMotDePasseRequest(
        @NotBlank(message = "Le mot de passe actuel est obligatoire")
        String motDePasseActuel,

        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Pattern(regexp = RegleMotDePasse.REGEX, message = RegleMotDePasse.MESSAGE)
        String nouveauMotDePasse
) {}