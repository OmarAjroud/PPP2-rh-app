package com.hrapp.rh.notification;

import com.hrapp.rh.common.dto.PageReponse;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.mail.MailMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationService {

    private static final int MAX_TITRE = 150;
    private static final int MAX_MESSAGE = 500;

    private final NotificationRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationProperties properties;
    private final Clock clock;
    private final String frontendUrl;

    public NotificationService(NotificationRepository repository,
                               ApplicationEventPublisher eventPublisher,
                               NotificationProperties properties,
                               Clock clock,
                               @Value("${app.frontend-url}") String frontendUrl) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.clock = clock;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    // ---------- création (appelée par les écouteurs métier) ----------

    /**
     * Enregistre une notification et, si c'est activé, envoie l'email correspondant.
     * Doit être appelée dans la transaction de l'action métier : rien n'est enregistré si elle échoue,
     * et l'email ne part qu'après la validation de la transaction.
     */
    @Transactional
    public Notification notify(Employe destinataire, TypeNotification type, String titre, String message,
                               String lien) {
        Notification notification = new Notification();
        notification.setDestinataire(destinataire);
        notification.setType(type);
        notification.setTitre(truncate(titre, MAX_TITRE));
        notification.setMessage(truncate(message, MAX_MESSAGE));
        notification.setLien(lien);
        notification.setDateCreation(LocalDateTime.now(clock));
        repository.save(notification);

        if (properties.emailEnabled() && destinataire.getEmail() != null) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("prenom", destinataire.getPrenom());
            variables.put("titre", notification.getTitre());
            variables.put("message", notification.getMessage());
            if (lien != null) {
                variables.put("lien", frontendUrl + lien);
            }
            eventPublisher.publishEvent(new MailMessage(destinataire.getEmail(),
                    notification.getTitre() + " – Application RH", "notification", variables));
        }
        return notification;
    }

    // ---------- consultation (utilisateur connecté) ----------

    /** Mes notifications, la plus récente d'abord ; seulement les non lues si demandé. */
    @Transactional(readOnly = true)
    public PageReponse<NotificationResponse> listMine(Long employeId, boolean nonLuesSeulement, int page, int taille) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), 100),
                Sort.by(Sort.Direction.DESC, "dateCreation", "id"));
        var resultat = nonLuesSeulement
                ? repository.findByDestinataireIdAndLueFalse(employeId, pageable)
                : repository.findByDestinataireId(employeId, pageable);
        return PageReponse.from(resultat.map(NotificationResponse::from));
    }

    @Transactional(readOnly = true)
    public long countUnread(Long employeId) {
        return repository.countByDestinataireIdAndLueFalse(employeId);
    }

    /** Marque une de mes notifications comme lue (404 si elle n'est pas à moi). Sans effet si déjà lue. */
    @Transactional
    public NotificationResponse markRead(Long employeId, Long id) {
        Notification notification = repository.findByIdAndDestinataireId(id, employeId)
                .orElseThrow(() -> new RessourceIntrouvableException("Notification introuvable (id=" + id + ")"));
        if (!notification.isLue()) {
            notification.setLue(true);
            notification.setDateLecture(LocalDateTime.now(clock));
        }
        return NotificationResponse.from(notification);
    }

    /** Marque toutes mes notifications comme lues ; renvoie combien l'ont été. */
    @Transactional
    public int markAllRead(Long employeId) {
        return repository.markAllRead(employeId, LocalDateTime.now(clock));
    }

    private static String truncate(String texte, int max) {
        return texte == null || texte.length() <= max ? texte : texte.substring(0, max - 1) + "…";
    }
}
