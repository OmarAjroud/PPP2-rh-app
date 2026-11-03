package com.hrapp.rh.audit;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record AuditEntryResponse(
        Long id,
        LocalDateTime dateAction,
        Long utilisateurId,
        String nomUtilisateur,
        String action,
        String entite,
        String entiteId,
        JsonNode anciennesValeurs,
        JsonNode nouvellesValeurs,
        String details,
        String adresseIp
) {}
