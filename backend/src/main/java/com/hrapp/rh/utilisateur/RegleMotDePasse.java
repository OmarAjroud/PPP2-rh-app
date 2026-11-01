package com.hrapp.rh.utilisateur;

/** Règle de validation des mots de passe (8 à 72 caractères, au moins une lettre et un chiffre). */
public final class RegleMotDePasse {

    // 72 = limite de BCrypt (les caractères au-delà sont ignorés)
    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String MESSAGE =
            "Le mot de passe doit contenir de 8 à 72 caractères, avec au moins une lettre et un chiffre";

    private RegleMotDePasse() {}
}