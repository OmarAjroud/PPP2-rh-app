package com.hrapp.rh.conge;

/** Statuts d'une demande de congé. */
public enum StatutDemandeConge {
    EN_ATTENTE("En attente"),
    APPROUVEE("Approuvée"),
    REFUSEE("Refusée"),
    ANNULEE("Annulée");

    private final String libelle;

    StatutDemandeConge(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}