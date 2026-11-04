package com.hrapp.rh.conge;

import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;

/** Règles d'accès aux congés d'un employé. */
public final class AccesConge {

    private AccesConge() {}

    /** Le MANAGER dont dépend directement l'employé (l'équipe = les employés dont manager_id est lui). */
    public static boolean isTeamManager(UtilisateurConnecte acteur, Employe employe) {
        return acteur.hasRole(Roles.MANAGER)
                && employe.getManager() != null
                && employe.getManager().getId().equals(acteur.getEmployeId());
    }

    /** Peut consulter les congés de cet employé : lui-même, son manager d'équipe, ADMIN et RH. */
    public static boolean canView(UtilisateurConnecte acteur, Employe employe) {
        return acteur.isAdminOrRh()
                || employe.getId().equals(acteur.getEmployeId())
                || isTeamManager(acteur, employe);
    }
}