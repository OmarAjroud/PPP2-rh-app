package com.hrapp.rh.conge;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Une ligne d'historique : le mouvement et le solde disponible juste après. */
public record MouvementSoldeResponse(
        Long id,
        LocalDateTime dateMouvement,
        String typeCongeCode,
        int annee,
        TypeMouvement typeMouvement,
        BigDecimal jours,
        BigDecimal soldeApres,
        Long demandeId,
        String commentaire,
        String creeParNomComplet
) {}