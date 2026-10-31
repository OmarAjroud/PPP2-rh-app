package com.hrapp.rh.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock private JavaMailSender mailSender;

    private MailService service;

    @BeforeEach
    void setUp() {
        // Vrai moteur de modèles, qui lit les vrais fichiers de src/main/resources/templates
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        service = new MailService(mailSender, engine, new MailProperties("no-reply@rh.test", "Application RH"));
    }

    @Test
    @DisplayName("Envoi : produit un email HTML en français adressé au bon destinataire")
    void send_shouldDeliverFrenchHtmlEmail_whenTemplateExists() throws Exception {
        stubMimeMessage();

        service.send(new MailMessage("yassine@rh.com", "Sujet de test", "test", Map.of("prenom", "Yassine")));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("yassine@rh.com");
        assertThat(sent.getSubject()).isEqualTo("Sujet de test");
        String html = String.valueOf(sent.getContent());
        assertThat(html).contains("Bonjour").contains("Yassine").contains("Application RH");
        assertThat(html).doesNotContain("${");          // aucun espace réservé non remplacé
    }

    @Test
    @DisplayName("Envoi : les valeurs sont échappées (pas d'injection HTML)")
    void send_shouldEscapeHtml_whenVariableContainsMarkup() throws Exception {
        stubMimeMessage();

        service.send(new MailMessage("x@rh.com", "Sujet", "test",
                Map.of("prenom", "<script>alert(1)</script>")));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        String html = String.valueOf(captor.getValue().getContent());

        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("Envoi : une panne SMTP ne fait pas échouer l'appelant")
    void send_shouldNotThrow_whenSmtpFails() throws Exception {
        stubMimeMessage();
        doThrow(new MailSendException("SMTP indisponible")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> service.send(
                new MailMessage("x@rh.com", "Sujet", "test", Map.of("prenom", "Lina"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Envoi : un modèle inexistant ne plante pas et n'envoie rien")
    void send_shouldNotSendAnything_whenTemplateDoesNotExist() {
        assertThatCode(() -> service.send(new MailMessage("x@rh.com", "Sujet", "inconnu", Map.of())))
                .doesNotThrowAnyException();

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private void stubMimeMessage() {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage(Session.getInstance(new Properties())));
    }
}