package com.hrapp.rh.audit;

import java.time.LocalDate;

/** Critères de recherche dans le journal (tous optionnels). */
public record AuditFilter(
        String nomUtilisateur,
        AuditAction action,
        String entite,
        String entiteId,
        LocalDate du,
        LocalDate au
) {}