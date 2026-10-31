package com.hrapp.rh.mail;

import java.util.Map;

/**
 * Un email à envoyer.
 *
 * @param template  nom du modèle HTML dans templates/mail (sans extension)
 * @param variables valeurs insérées dans le modèle
 */
public record MailMessage(String to, String subject, String template, Map<String, Object> variables) {

    public MailMessage {
        variables = (variables == null) ? Map.of() : Map.copyOf(variables);
    }
}