package com.hrapp.rh.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MailTemplatesTest {

    private SpringTemplateEngine engine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
    }

    @Test
    @DisplayName("Modèle activation : contient le prénom, le lien et la durée de validité")
    void activationTemplate_shouldContainNameLinkAndValidity() {
        String lien = "http://localhost:4200/activate-account?token=ABC123";

        String html = render("activation", Map.of("prenom", "Lina", "lien", lien, "validiteHeures", 48L));

        assertThat(html).contains("Bonjour").contains("Lina").contains(lien).contains("48")
                .contains("Activer mon compte").doesNotContain("${");
    }

    @Test
    @DisplayName("Modèle réinitialisation : contient le prénom, le lien et la durée de validité")
    void resetTemplate_shouldContainNameLinkAndValidity() {
        String lien = "http://localhost:4200/reset-password?token=XYZ789";

        String html = render("reinitialisation", Map.of("prenom", "Lina", "lien", lien, "validiteMinutes", 60L));

        assertThat(html).contains("Lina").contains(lien).contains("60")
                .contains("nouveau mot de passe").doesNotContain("${");
    }

    @Test
    @DisplayName("Modèle mot de passe modifié : contient le prénom et aucun lien")
    void passwordChangedTemplate_shouldContainNameAndNoLink() {
        String html = render("mot-de-passe-modifie", Map.of("prenom", "Lina"));

        assertThat(html).contains("Lina").contains("modifié").doesNotContain("href=\"http").doesNotContain("${");
    }

    @Test
    @DisplayName("Modèle notification : titre, message et bouton vers l'application")
    void notificationTemplate_shouldContainTitleMessageAndLink() {
        String lien = "http://localhost:4200/leave-requests/10";

        String html = render("notification", Map.of("prenom", "Karim", "titre", "Nouvelle demande de congé",
                "message", "Lina Gharbi a demandé : Congé annuel", "lien", lien));

        assertThat(html).contains("Karim").contains("Nouvelle demande de congé")
                .contains("Lina Gharbi a demandé").contains(lien).contains("Voir dans l'application")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("Modèle notification : pas de bouton quand il n'y a pas de lien")
    void notificationTemplate_shouldHideButton_whenThereIsNoLink() {
        String html = render("notification", Map.of("prenom", "Karim", "titre", "Titre", "message", "Message"));

        assertThat(html).doesNotContain("Voir dans l'application").doesNotContain("href=\"http");
    }

    private String render(String template, Map<String, Object> variables) {
        return engine.process("mail/" + template, new Context(Locale.FRENCH, variables));
    }
}