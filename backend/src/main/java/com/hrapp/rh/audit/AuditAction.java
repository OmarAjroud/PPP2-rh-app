package com.hrapp.rh.audit;

/** Types d'actions enregistrées dans le journal. */
public enum AuditAction {
    CREATION,
    MODIFICATION,
    SUPPRESSION,
    CONNEXION,
    ECHEC_CONNEXION,
    INVITATION,
    ACTIVATION_COMPTE,
    CHANGEMENT_MOT_DE_PASSE,
    REINITIALISATION_MOT_DE_PASSE,
    APPEL_ASSISTANT              // un outil de l'assistant RH a été exécuté (chat ou MCP)
}