package com.hrapp.rh.notification;

/** Événements internes publiés par NotificationService, relayés en direct (SSE) après validation de la transaction. */
public final class NotificationEvenements {

    private NotificationEvenements() {}

    /** Une notification vient d'être créée pour cet employé. */
    public record Creee(Long destinataireId, NotificationResponse notification) {}

    /** Le nombre de notifications non lues de cet employé a changé (lecture). */
    public record NonLuesModifiees(Long employeId) {}
}
