package com.hrapp.rh.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Conversation envoyée par le front-end (l'API ne garde aucun historique) : les messages précédents
 * puis la nouvelle question, qui doit être le dernier message.
 */
public record ChatRequest(
        @NotEmpty(message = "La conversation est vide")
        @Size(max = 20, message = "La conversation ne peut pas dépasser 20 messages : commencez-en une nouvelle")
        List<@Valid Message> messages
) {

    public record Message(
            @NotBlank(message = "Le rôle est obligatoire")
            @Pattern(regexp = "utilisateur|assistant", message = "Le rôle doit être « utilisateur » ou « assistant »")
            String role,

            @NotBlank(message = "Le message est vide")
            @Size(max = 4000, message = "Un message ne peut pas dépasser 4000 caractères")
            String contenu
    ) {
        boolean deLUtilisateur() {
            return "utilisateur".equals(role);
        }
    }
}
