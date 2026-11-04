package com.hrapp.rh.notification;

import com.hrapp.rh.conge.DemandeConge;
import com.hrapp.rh.conge.DemandeCongeEvenement;
import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.employe.EmployeRepository;
import com.hrapp.rh.securite.Roles;
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
 *   <li>nouvelle demande : son manager direct ; à défaut les RH ; à défaut les administrateurs ;</li>
 *   <li>approbation / refus : le demandeur ;</li>
 *   <li>annulation : les mêmes valideurs, plus celui qui avait pris la décision.</li>
 * </ul>
 * Personne n'est jamais notifié de sa propre action.
 */
@Component
@RequiredArgsConstructor
public class CongeNotificationListener {

    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NotificationService notificationService;
    private final EmployeRepository employeRepository;

    @EventListener
    public void onDemandeConge(DemandeCongeEvenement evenement) {
        DemandeConge demande = evenement.demande();
        switch (evenement.type()) {
            case CREEE -> notifyAll(validators(demande), TypeNotification.CONGE_DEMANDE,
                    "Nouvelle demande de congé",
                    fullName(demande.getEmploye()) + " a demandé : " + summary(demande) + ".",
                    demande);
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

    /** Qui doit valider cette demande : le manager direct, sinon les RH, sinon les administrateurs. */
    private Map<Long, Employe> validators(DemandeConge demande) {
        Map<Long, Employe> destinataires = new LinkedHashMap<>();
        Employe manager = demande.getEmploye().getManager();
        if (manager != null && !isRequester(demande, manager)) {
            destinataires.put(manager.getId(), manager);
            return destinataires;
        }
        addAllExceptRequester(destinataires, employeRepository.findByRoleNom(Roles.RH), demande);
        if (destinataires.isEmpty()) {
            addAllExceptRequester(destinataires, employeRepository.findByRoleNom(Roles.ADMIN), demande);
        }
        return destinataires;
    }

    private void addAllExceptRequester(Map<Long, Employe> cible, List<Employe> candidats, DemandeConge demande) {
        candidats.stream()
                .filter(e -> !isRequester(demande, e))
                .forEach(e -> cible.putIfAbsent(e.getId(), e));
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
