package com.hrapp.rh.notification;

import com.hrapp.rh.conge.DemandeConge;
import com.hrapp.rh.conge.DemandeCongeEvenement;
import com.hrapp.rh.conge.RegleDecisionConge;
import com.hrapp.rh.conge.StatutDemandeConge;
import com.hrapp.rh.conge.TypeConge;
import com.hrapp.rh.employe.Employe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Contenu et destinataires des notifications ; QUI doit décider est testé dans RegleDecisionCongeTest. */
@ExtendWith(MockitoExtension.class)
class CongeNotificationListenerTest {

    @Mock private NotificationService notificationService;
    @Mock private RegleDecisionConge regle;
    @InjectMocks private CongeNotificationListener listener;

    private final Employe sara = employe(2L, "Sara", "Trabelsi", null);      // RH
    private final Employe karim = employe(3L, "Karim", "Mansour", null);     // MANAGER
    private final Employe lina = employe(4L, "Lina", "Gharbi", karim);       // équipe de Karim
    private final TypeConge annuel = annuel();

    @Test
    @DisplayName("Nouvelle demande : ceux qui doivent décider sont notifiés, avec un résumé et un lien")
    void created_shouldNotifyTheDeciders() {
        DemandeConge demande = demande(lina, StatutDemandeConge.EN_ATTENTE);
        given(regle.deciders(demande)).willReturn(List.of(karim));

        listener.onDemandeConge(new DemandeCongeEvenement(DemandeCongeEvenement.Type.CREEE, demande));

        verify(notificationService).notify(eq(karim), eq(TypeNotification.CONGE_DEMANDE),
                eq("Nouvelle demande de congé"),
                eq("Lina Gharbi a demandé : Congé annuel du 14/12/2026 au 18/12/2026, 5 jours ouvrables."),
                eq("/conges/demandes/10"));
        verify(notificationService, times(1)).notify(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Transmise aux RH : les RH sont prévenues qu'elles peuvent traiter, le manager reçoit un rappel")
    void escalated_shouldNotifyHr_andRemindTheManager() {
        DemandeConge demande = demande(lina, StatutDemandeConge.EN_ATTENTE);
        given(regle.fallbackDeciders(demande)).willReturn(List.of(sara));
        given(regle.activeManager(demande)).willReturn(Optional.of(karim));

        listener.onDemandeConge(new DemandeCongeEvenement(DemandeCongeEvenement.Type.ESCALADEE, demande));

        verify(notificationService).notify(eq(sara), eq(TypeNotification.CONGE_A_TRAITER),
                eq("Demande de congé à traiter"), contains("vous pouvez la traiter"), eq("/conges/demandes/10"));
        verify(notificationService).notify(eq(karim), eq(TypeNotification.CONGE_A_TRAITER),
                eq("Rappel : demande de congé en attente"), contains("attend toujours votre décision"), anyString());
    }

    @Test
    @DisplayName("Approbation : le demandeur est notifié avec le nom du décideur et le commentaire")
    void approved_shouldNotifyRequester() {
        DemandeConge demande = demande(lina, StatutDemandeConge.APPROUVEE);
        demande.setDecidePar(karim);
        demande.setMotifDecision("Bon repos");

        listener.onDemandeConge(new DemandeCongeEvenement(DemandeCongeEvenement.Type.APPROUVEE, demande));

        verify(notificationService).notify(eq(lina), eq(TypeNotification.CONGE_APPROUVE),
                eq("Demande de congé approuvée"),
                eq("Votre demande (Congé annuel du 14/12/2026 au 18/12/2026, 5 jours ouvrables) "
                        + "a été approuvée par Karim Mansour. Commentaire : Bon repos"),
                eq("/conges/demandes/10"));
    }

    @Test
    @DisplayName("Refus : le demandeur est notifié avec le motif")
    void rejected_shouldNotifyRequesterWithReason() {
        DemandeConge demande = demande(lina, StatutDemandeConge.REFUSEE);
        demande.setDecidePar(karim);
        demande.setMotifDecision("Période de clôture");

        listener.onDemandeConge(new DemandeCongeEvenement(DemandeCongeEvenement.Type.REFUSEE, demande));

        verify(notificationService).notify(eq(lina), eq(TypeNotification.CONGE_REFUSE),
                eq("Demande de congé refusée"), contains("Motif : Période de clôture"), eq("/conges/demandes/10"));
    }

    @Test
    @DisplayName("Annulation d'un congé approuvé en recours par les RH : le manager et la RH qui a décidé sont notifiés")
    void cancelled_shouldNotifyDecidersAndDecider() {
        DemandeConge demande = demande(lina, StatutDemandeConge.ANNULEE);
        demande.setDecidePar(sara);
        given(regle.deciders(demande)).willReturn(List.of(karim));

        listener.onDemandeConge(new DemandeCongeEvenement(DemandeCongeEvenement.Type.ANNULEE, demande));

        verify(notificationService).notify(eq(karim), eq(TypeNotification.CONGE_ANNULE), anyString(),
                eq("Lina Gharbi a annulé sa demande (Congé annuel du 14/12/2026 au 18/12/2026, 5 jours ouvrables)."),
                anyString());
        verify(notificationService).notify(eq(sara), eq(TypeNotification.CONGE_ANNULE), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Annulation approuvée par le manager lui-même : il n'est notifié qu'une seule fois")
    void cancelled_shouldNotNotifyTwice_whenManagerIsAlsoTheDecider() {
        DemandeConge demande = demande(lina, StatutDemandeConge.ANNULEE);
        demande.setDecidePar(karim);
        given(regle.deciders(demande)).willReturn(List.of(karim));

        listener.onDemandeConge(new DemandeCongeEvenement(DemandeCongeEvenement.Type.ANNULEE, demande));

        verify(notificationService, times(1)).notify(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Interruption : le demandeur (motif compris) et son manager sont prévenus ; « écourté » si le congé continue")
    void interrupted_shouldNotifyRequesterAndManager() {
        DemandeConge demande = demande(lina, StatutDemandeConge.APPROUVEE);
        demande.setMotifInterruption("Retour anticipé");
        given(regle.activeManager(demande)).willReturn(Optional.of(karim));

        listener.onDemandeConge(new DemandeCongeEvenement(DemandeCongeEvenement.Type.INTERROMPUE, demande));

        verify(notificationService).notify(eq(lina), eq(TypeNotification.CONGE_INTERROMPU), eq("Congé interrompu"),
                contains("écourté par les ressources humaines. Motif : Retour anticipé"), anyString());
        verify(notificationService).notify(eq(karim), eq(TypeNotification.CONGE_INTERROMPU), anyString(), anyString(),
                anyString());
    }

    @Test
    @DisplayName("Interruption d'un congé pas commencé : « annulé » ; sans manager, seul le demandeur est prévenu")
    void interrupted_beforeStart_shouldSayCancelled() {
        DemandeConge demande = demande(lina, StatutDemandeConge.ANNULEE);
        demande.setMotifInterruption("Besoin urgent");
        given(regle.activeManager(demande)).willReturn(Optional.empty());

        listener.onDemandeConge(new DemandeCongeEvenement(DemandeCongeEvenement.Type.INTERROMPUE, demande));

        verify(notificationService).notify(eq(lina), any(), anyString(), contains("a été annulé par"), anyString());
        verify(notificationService, never()).notify(eq(karim), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Résumé : « 1 jour ouvrable » au singulier")
    void summary_shouldUseSingular_forOneDayOrLess() {
        DemandeConge demande = demande(lina, StatutDemandeConge.EN_ATTENTE);
        demande.setNombreJours(new BigDecimal("1.0"));
        given(regle.deciders(demande)).willReturn(List.of(karim));

        listener.onDemandeConge(new DemandeCongeEvenement(DemandeCongeEvenement.Type.CREEE, demande));

        verify(notificationService).notify(eq(karim), any(), anyString(), contains(", 1 jour ouvrable."), anyString());
    }

    // ---------- méthodes utilitaires ----------

    private static Employe employe(Long id, String prenom, String nom, Employe manager) {
        Employe e = new Employe();
        e.setId(id);
        e.setPrenom(prenom);
        e.setNom(nom);
        e.setManager(manager);
        return e;
    }

    private static TypeConge annuel() {
        TypeConge t = new TypeConge();
        t.setId(1L);
        t.setCode("ANNUEL");
        t.setLibelle("Congé annuel");
        t.setDeduitDuSolde(true);
        return t;
    }

    private DemandeConge demande(Employe employe, StatutDemandeConge statut) {
        DemandeConge d = new DemandeConge();
        d.setId(10L);
        d.setEmploye(employe);
        d.setTypeConge(annuel);
        d.setDateDebut(LocalDate.of(2026, 12, 14));
        d.setDateFin(LocalDate.of(2026, 12, 18));
        d.setNombreJours(new BigDecimal("5.0"));
        d.setStatut(statut);
        return d;
    }
}
