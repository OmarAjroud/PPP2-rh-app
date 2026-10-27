package com.hrapp.rh.employe;

/** Vue réduite d'un employé (sans salaire), utilisée dans le détail d'un département. */
public record EmployeResume(Long id, String nom, String prenom, String email) {}