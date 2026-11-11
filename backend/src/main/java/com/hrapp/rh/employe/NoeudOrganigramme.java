package com.hrapp.rh.employe;

/**
 * Une personne dans l'organigramme : identité, poste et rattachement. Jamais le salaire.
 * managerId null : personne au sommet d'une branche.
 */
public record NoeudOrganigramme(
        Long id,
        String nom,
        String prenom,
        String email,
        String roleNom,
        Long departementId,
        String departementNom,
        Long managerId
) {}
