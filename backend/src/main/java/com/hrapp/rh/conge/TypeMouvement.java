package com.hrapp.rh.conge;

/** Nature d'un mouvement de solde. */
public enum TypeMouvement {
    ALLOCATION,       // allocation annuelle initiale
    DEDUCTION,        // validation d'une demande
    REMBOURSEMENT,    // annulation d'un congé approuvé
    AJUSTEMENT        // correction par les RH
}