package com.hrapp.rh.notification;

/** Nature d'une notification (la liste s'enrichira avec les prochaines fonctionnalités). */
public enum TypeNotification {
    CONGE_DEMANDE,     // une demande de mon équipe attend ma décision
    CONGE_APPROUVE,    // ma demande a été approuvée
    CONGE_REFUSE,      // ma demande a été refusée
    CONGE_ANNULE,      // une demande de mon équipe a été annulée par son auteur
    CONGE_A_TRAITER,   // une demande attend depuis plusieurs jours : transmise aux RH (rappel au manager)
    CONGE_INTERROMPU   // un congé approuvé a été interrompu par les RH
}
