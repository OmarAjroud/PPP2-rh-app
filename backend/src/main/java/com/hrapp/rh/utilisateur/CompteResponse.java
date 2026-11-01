package com.hrapp.rh.utilisateur;

public record CompteResponse(Long id, Long employeId, String nomUtilisateur, StatutCompte statut) {}