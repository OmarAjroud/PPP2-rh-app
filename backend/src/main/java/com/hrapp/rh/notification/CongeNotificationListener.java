package com.hrapp.rh.notification;

import com.hrapp.rh.conge.DemandeConge;
import com.hrapp.rh.conge.DemandeCongeEvenement;
import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.conge.RegleDecisionConge;
import com.hrapp.rh.conge.StatutDemandeConge;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforme les événements des demandes de congé en notifications.
 * Écouteur synchrone : il s'exécute dans la transaction de la demande (tout est enregistré, ou rien).
 *
 * <ul>
 *   <li>nouvelle demande : ceux qui doivent décider (RegleDecisionConge) : le manager direct s'il peut agir,
 *       sinon les RH, sinon les administrateurs ;</li>
 *   <li>demande transmise aux RH faute de décision : les RH (ou ADMIN), et un rappel au manager ;</li>
 *   <li>approbation / refus : le demandeur ;</li>
 *   <li>annulation : les mêmes décideurs, plus celui qui avait pris la décision ;</li>
 *   <li>interruption par les RH : le demandeur et son manager.</li>
 * </ul>
 * Personne n'est jamais notifié de sa propre action.
 */
@Component
@RequiredArgsConstructor
public class CongeNotificationListener {

    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NotificationService notificationService;
    private final RegleDecisionConge regle;

    @EventListener
    public void onDemandeConge(DemandeCongeEvenement evenement) {
        DemandeConge demande = evenement.demande();
        switch (evenement.type()) {
            case CREEE -> notifyAll(validators(demande), TypeNotification.CONGE_DEMANDE,
                    "Nouvelle demande de congé",
                    fullName(demande.getEmploye()) + " a demandé : " + summary(demande) + ".",
                    demande);
            case ESCALADEE -> {
                // les RH peuvent désormais décider ; le manager, s'il peut agir, reçoit un rappel
                notifyAll(asMap(regle.fallbackDeciders(demande)), TypeNotification.CONGE_A_TRAITER,
                        "Demande de congé à traiter",
                        "La demande de " + fullName(demande.getEmploye()) + " (" + summary(demande)
                                + ") attend une décision du manager depuis plusieurs jours : vous pouvez la traiter.",
                        demande);
                regle.activeManager(demande).ifPresent(manager -> notificationService.notify(manager,
                        TypeNotification.CONGE_A_TRAITER, "Rappel : demande de congé en attente",
                        "La demande de " + fullName(demande.getEmploye()) + " (" + summary(demande)
                                + ") attend toujours votre décision ; les RH peuvent maintenant la traiter.",
                        link(demande)));
            }
            case INTERROMPUE -> {
                String message = "Votre congé (" + summary(demande) + ") a été "
                        + (demande.getStatut() == StatutDemandeConge.ANNULEE ? "annulé" : "écourté")
                        + " par les ressources humaines. Motif : " + demande.getMotifInterruption();
                notificationService.notify(demande.getEmploye(), TypeNotification.CONGE_INTERROMPU,
                        "Congé interrompu", message, link(demande));
                regle.activeManager(demande).ifPresent(manager -> notificationService.notify(manager,
                        TypeNotification.CONGE_INTERROMPU, "Congé interrompu",
                        "Le congé de " + fullName(demande.getEmploye()) + " (" + summary(demande)
                                + ") a été interrompu par les ressources humaines.", link(demande)));
            }
            case APPROUVEE -> notificationService.notify(demande.getEmploye(), TypeNotification.CONGE_APPROUVE,
                    "Demande de congé approuvée",
                    "Votre demande (" + summary(demande) + ") a été approuvée par "
                            + fullName(demande.getDecidePar()) + "." + reason("Commentaire", demande),
                    link(demande));
            case REFUSEE -> notificationService.notify(demande.getEmploye(), TypeNotification.CONGE_REFUSE,
                    "Demande de congé refusée",
                    "Votre demande (" + summary(demande) + ") a été refusée par "
                            + fullName(demande.getDecidePar()) + "." + reason("Motif", demande),
                    link(demande));
            case ANNULEE -> {
                Map<Long, Employe> destinataires = validators(demande);
                if (demande.getDecidePar() != null && !isRequester(demande, demande.getDecidePar())) {
                    destinataires.putIfAbsent(demande.getDecidePar().getId(), demande.getDecidePar());
                }
                notifyAll(destinataires, TypeNotification.CONGE_ANNULE, "Demande de congé annulée",
                        fullName(demande.getEmploye()) + " a annulé sa demande (" + summary(demande) + ").",
                        demande);
            }
        }
    }

    // ---------- méthodes privées ----------

    /** Qui doit décider : le manager direct s'il peut agir, sinon les RH, sinon les administrateurs (RegleDecisionConge). */
    private Map<Long, Employe> validators(DemandeConge demande) {
        return asMap(regle.deciders(demande));
    }

    private static Map<Long, Employe> asMap(List<Employe> employes) {
        Map<Long, Employe> parId = new LinkedHashMap<>();
        employes.forEach(e -> parId.putIfAbsent(e.getId(), e));
        return parId;
    }

    private void notifyAll(Map<Long, Employe> destinataires, TypeNotification type, String titre, String message,
                           DemandeConge demande) {
        Collection<Employe> liste = destinataires.values();
        liste.forEach(d -> notificationService.notify(d, type, titre, message, link(demande)));
    }

    private boolean isRequester(DemandeConge demande, Employe employe) {
        return employe.getId().equals(demande.getEmploye().getId());
    }

    /** « Congé annuel du 07/12/2026 au 11/12/2026, 5 jours ouvrables » */
    private String summary(DemandeConge demande) {
        BigDecimal jours = demande.getNombreJours();
        String nombre = jours.stripTrailingZeros().toPlainString();
        String unite = jours.compareTo(BigDecimal.ONE) > 0 ? " jours ouvrables" : " jour ouvrable";
        return demande.getTypeConge().getLibelle()
                + " du " + demande.getDateDebut().format(FORMAT_DATE)
                + " au " + demande.getDateFin().format(FORMAT_DATE)
                + ", " + nombre + unite;
    }

    private String reason(String etiquette, DemandeConge demande) {
        String motif = demande.getMotifDecision();
        return motif == null || motif.isBlank() ? "" : " " + etiquette + " : " + motif;
    }

    private String fullName(Employe employe) {
        return employe == null ? "?" : employe.getPrenom() + " " + employe.getNom();
    }

    private String link(DemandeConge demande) {
        return "/conges/demandes/" + demande.getId();
    }
}
