package com.hrapp.rh.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres des notifications (section app.notifications de application.yml).
 *
 * @param emailEnabled envoyer aussi un email pour chaque notification (true par défaut)
 */
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(Boolean emailEnabled) {

    public NotificationProperties {
        emailEnabled = emailEnabled == null || emailEnabled;
    }
}
