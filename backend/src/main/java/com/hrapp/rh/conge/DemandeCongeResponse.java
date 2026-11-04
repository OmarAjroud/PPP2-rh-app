package com.hrapp.rh.conge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DemandeCongeResponse(
        Long id,
        Long employeId,
        String employeNomComplet,
        String employeDepartementNom,
        Long typeCongeId,
        String typeCongeCode,
        String typeCongeLibelle,
        LocalDate dateDebut,
        LocalDate dateFin,
        BigDecimal nombreJours,
        String commentaire,
        StatutDemandeConge statut,
        LocalDateTime dateCreation,
        String decideParNomComplet,
        LocalDateTime dateDecision,
        String motifDecision
) {}