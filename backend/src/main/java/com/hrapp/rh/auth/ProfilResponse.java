package com.hrapp.rh.auth;

/** Informations sur l'utilisateur connecté. */
public record ProfilResponse(String nomUtilisateur, String role, Long employeId, Long departementId) {}