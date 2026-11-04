package com.hrapp.rh.tableaudebord;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tableau de bord : indicateurs clés et données des graphiques, pour une année et un périmètre.
 *
 * @param departementId  périmètre : un département, ou null pour toute l'entreprise
 * @param masseSalariale réservée à ADMIN et RH : null pour les autres profils
 */
public record TableauDeBordResponse(
        int annee,
        Long departementId,
        Indicateurs indicateurs,
        List<EffectifDepartement> effectifParDepartement,
        List<EmbauchesMois> embauchesParMois,
        List<RepartitionConge> repartitionConges,
        List<MasseSalarialeDepartement> masseSalariale
) {

    public record Indicateurs(long nombreEmployes, long demandesEnAttente, long embauchesDuMois,
                              long absentsAujourdhui) {}

    public record EffectifDepartement(Long departementId, String departementNom, long nombreEmployes) {}

    /** mois : 1 (janvier) à 12 (décembre) ; les 12 mois sont toujours présents. */
    public record EmbauchesMois(int mois, long nombre) {}

    /** Congés approuvés commençant dans l'année, par type. */
    public record RepartitionConge(String typeCongeCode, String typeCongeLibelle, long nombreDemandes,
                                   BigDecimal nombreJours) {}

    public record MasseSalarialeDepartement(Long departementId, String departementNom, long nombreEmployes,
                                            BigDecimal total, BigDecimal moyenne) {}
}
