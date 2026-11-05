package com.hrapp.rh.assistant;

/**
 * Résultat d'un outil : du JSON en cas de succès, un message lisible en cas d'erreur
 * (accès refusé, introuvable, paramètre invalide). Une erreur n'interrompt pas la conversation :
 * le modèle la lit et l'explique à l'utilisateur.
 */
public record ResultatOutil(boolean erreur, String contenu) {

    static ResultatOutil succes(String json) {
        return new ResultatOutil(false, json);
    }

    static ResultatOutil echec(String message) {
        return new ResultatOutil(true, message);
    }
}
