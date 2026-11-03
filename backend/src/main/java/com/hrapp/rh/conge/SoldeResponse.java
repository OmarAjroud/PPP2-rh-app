package com.hrapp.rh.conge;

import java.math.BigDecimal;

/** Solde d'un type de congé pour une année : alloué, pris, réservé par des demandes en attente, disponible. */
public record SoldeResponse(
        Long typeCongeId,
        String typeCongeCode,
        String typeCongeLibelle,
        int annee,
        BigDecimal joursAlloues,
        BigDecimal joursPris,
        BigDecimal joursEnAttente,
        BigDecimal joursDisponibles
) {}