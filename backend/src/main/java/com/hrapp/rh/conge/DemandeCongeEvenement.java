package com.hrapp.rh.conge;

/**
 * Événement publié à chaque étape du cycle de vie d'une demande de congé.
 * Les écouteurs (notifications...) le reçoivent dans la même transaction que la modification.
 *
 * @param type    ce qui vient de se passer
 * @param demande la demande, déjà dans son nouvel état
 */
public record DemandeCongeEvenement(Type type, DemandeConge demande) {

    public enum Type {
        CREEE,
        APPROUVEE,
        REFUSEE,
        ANNULEE,
        ESCALADEE,     // restée sans décision du manager : transmise aux RH
        INTERROMPUE    // congé approuvé interrompu par les RH
    }
}
