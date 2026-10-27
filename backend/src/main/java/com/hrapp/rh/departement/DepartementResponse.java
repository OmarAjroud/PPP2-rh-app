package com.hrapp.rh.departement;

/** Département avec son nombre d'employés (liste). */
public record DepartementResponse(Long id, String nom, String description, Long nombreEmployes) {}