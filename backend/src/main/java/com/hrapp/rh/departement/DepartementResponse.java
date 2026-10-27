package com.hrapp.rh.departement;

/** Données renvoyées par l'API : on n'expose jamais l'entité directement. */
public record DepartementResponse(Long id, String nom, String description) {

}