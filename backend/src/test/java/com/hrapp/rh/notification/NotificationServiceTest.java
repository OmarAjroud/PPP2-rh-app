package com.hrapp.rh.notification;

import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.mail.MailMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository repository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T09:30:00Z"), ZoneOffset.UTC);
    private final LocalDateTime maintenant = LocalDateTime.of(2026, 11, 5, 9, 30);

    private NotificationService service(boolean emailEnabled) {
        return new NotificationService(repository, eventPublisher, new NotificationProperties(emailEnabled),
                clock, "http://localhost:4200/");
    }

    @Test
    @DisplayName("Création : enregistre la notification non lue et envoie l'email avec un lien absolu")
    void notify_shouldSaveUnreadNotificationAndSendEmail() {
        Notification n = service(true).notify(karim(), TypeNotification.CONGE_DEMANDE,
                "Nouvelle demande de congé", "Lina Gharbi a demandé…", "/conges/demandes/10");

        verify(repository).save(n);
        assertThat(n.isLue()).isFalse();
        assertThat(n.getDateCreation()).isEqualTo(maintenant);
        assertThat(n.getDestinataire().getId()).isEqualTo(3L);

        MailMessage mail = published(MailMessage.class).getFirst();
        assertThat(mail.to()).isEqualTo("manager@rh.com");
        assertThat(mail.template()).isEqualTo("notification");
        assertThat(mail.subject()).startsWith("Nouvelle demande de congé");
        assertThat(mail.variables())
                .containsEntry("prenom", "Karim")
                .containsEntry("lien", "http://localhost:4200/conges/demandes/10");   // pas de « // »
    }

    @Test
    @DisplayName("Création : annonce la notification pour la poussée en direct (SSE)")
    void notify_shouldPublishCreatedEvent_forLivePush() {
        service(false).notify(karim(), TypeNotification.CONGE_DEMANDE, "Titre", "Message", "/conges/demandes/10");

        NotificationEvenements.Creee evenement = published(NotificationEvenements.Creee.class).getFirst();
        assertThat(evenement.destinataireId()).isEqualTo(3L);
        assertThat(evenement.notification().titre()).isEqualTo("Titre");
        assertThat(published(MailMessage.class)).isEmpty();
    }

    @Test
    @DisplayName("Lecture : prévient les autres onglets, seulement si le compteur change")
    void markRead_shouldAnnounceUnreadChange_onlyWhenSomethingChanged() {
        given(repository.findByIdAndDestinataireId(7L, 3L)).willReturn(Optional.of(notification(7L, false)));
        given(repository.findByIdAndDestinataireId(8L, 3L)).willReturn(Optional.of(notification(8L, true)));

        service(true).markRead(3L, 7L);
        service(true).markRead(3L, 8L);                        // déjà lue : rien à annoncer

        assertThat(published(NotificationEvenements.NonLuesModifiees.class))
                .containsExactly(new NotificationEvenements.NonLuesModifiees(3L));
    }

    @Test
    @DisplayName("Tout marquer comme lu : aucune annonce s'il n'y avait rien à lire")
    void markAllRead_shouldNotAnnounce_whenNothingWasUnread() {
        given(repository.markAllRead(3L, maintenant)).willReturn(0);

        service(true).markAllRead(3L);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Création : aucun email quand les emails de notification sont désactivés")
    void notify_shouldNotSendEmail_whenDisabled() {
        service(false).notify(karim(), TypeNotification.CONGE_DEMANDE, "Titre", "Message", null);

        verify(repository).save(any(Notification.class));
        assertThat(published(MailMessage.class)).isEmpty();
    }

    @Test
    @DisplayName("Création : un message trop long est tronqué (jamais d'erreur SQL)")
    void notify_shouldTruncateLongMessage() {
        Notification n = service(false).notify(karim(), TypeNotification.CONGE_DEMANDE,
                "Titre", "x".repeat(800), null);

        assertThat(n.getMessage()).hasSize(500).endsWith("…");
    }

    @Test
    @DisplayName("Création : la date est arrondie à la seconde, comme en base (version poussée = version relue)")
    void notify_shouldTruncateCreationDateToSeconds() {
        Clock precise = Clock.fixed(Instant.parse("2026-11-05T09:30:00.539824Z"), ZoneOffset.UTC);
        NotificationService service = new NotificationService(repository, eventPublisher,
                new NotificationProperties(false), precise, "http://localhost:4200");

        Notification n = service.notify(karim(), TypeNotification.CONGE_DEMANDE, "Titre", "Message", null);

        assertThat(n.getDateCreation()).isEqualTo(maintenant);
    }

    @Test
    @DisplayName("Lecture : marque ma notification comme lue avec la date")
    void markRead_shouldSetReadFlagAndDate() {
        Notification n = notification(7L, false);
        given(repository.findByIdAndDestinataireId(7L, 3L)).willReturn(Optional.of(n));

        NotificationResponse result = service(true).markRead(3L, 7L);

        assertThat(result.lue()).isTrue();
        assertThat(result.dateLecture()).isEqualTo(maintenant);
    }

    @Test
    @DisplayName("Lecture : une notification déjà lue garde sa date de lecture d'origine")
    void markRead_shouldKeepOriginalDate_whenAlreadyRead() {
        Notification n = notification(7L, true);
        LocalDateTime premiereLecture = LocalDateTime.of(2026, 11, 1, 8, 0);
        n.setDateLecture(premiereLecture);
        given(repository.findByIdAndDestinataireId(7L, 3L)).willReturn(Optional.of(n));

        assertThat(service(true).markRead(3L, 7L).dateLecture()).isEqualTo(premiereLecture);
    }

    @Test
    @DisplayName("Lecture : la notification d'un autre utilisateur est introuvable (404)")
    void markRead_shouldThrowNotFound_whenNotificationBelongsToSomeoneElse() {
        given(repository.findByIdAndDestinataireId(7L, 4L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service(true).markRead(4L, 7L))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    @DisplayName("Tout marquer comme lu : ne touche que mes notifications")
    void markAllRead_shouldOnlyTouchMyNotifications() {
        given(repository.markAllRead(3L, maintenant)).willReturn(4);

        assertThat(service(true).markAllRead(3L)).isEqualTo(4);
    }

    @Test
    @DisplayName("Liste : filtre « non lues » et taille de page plafonnée à 100")
    void listMine_shouldUseUnreadQuery_andCapPageSize() {
        Page<Notification> page = new PageImpl<>(List.of(notification(1L, false)));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        given(repository.findByDestinataireIdAndLueFalse(eq(3L), pageable.capture())).willReturn(page);

        var result = service(true).listMine(3L, true, -2, 500);

        assertThat(result.contenu()).hasSize(1);
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        verify(repository, never()).findByDestinataireId(any(), any());
    }

    // ---------- méthodes utilitaires ----------

    /** Événements d'un type donné publiés par le service (il en publie de plusieurs types). */
    private <T> List<T> published(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(type::isInstance).map(type::cast).toList();
    }

    private Employe karim() {
        Employe e = new Employe();
        e.setId(3L);
        e.setPrenom("Karim");
        e.setNom("Mansour");
        e.setEmail("manager@rh.com");
        return e;
    }

    private Notification notification(Long id, boolean lue) {
        Notification n = new Notification();
        n.setId(id);
        n.setDestinataire(karim());
        n.setType(TypeNotification.CONGE_DEMANDE);
        n.setTitre("Titre");
        n.setMessage("Message");
        n.setLue(lue);
        n.setDateCreation(LocalDateTime.of(2026, 11, 4, 12, 0));
        return n;
    }
}
