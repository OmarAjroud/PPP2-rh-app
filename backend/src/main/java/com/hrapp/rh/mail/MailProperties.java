package com.hrapp.rh.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Paramètres d'expédition (section app.mail de application.yml). */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String from, String fromName) {}