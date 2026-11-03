package com.hrapp.rh.audit;

import java.time.LocalDateTime;

/** Une ligne du journal, prête à être écrite. */
public record AuditRecord(
        LocalDateTime dateAction,
        Long utilisateurId,
        String nomUtilisateur,
        AuditAction action,
        String entite,
        String entiteId,
        String anciennesValeurs,
        String nouvellesValeurs,
        String details,
        String adresseIp
) {}