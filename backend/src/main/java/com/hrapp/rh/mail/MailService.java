package com.hrapp.rh.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailProperties properties;

    /** Envoi asynchrone : s'exécute dans un autre thread, l'appelant n'attend pas. */
    @Async
    public void send(MailMessage message) {
        try {
            Context context = new Context(Locale.FRENCH, message.variables());
            String html = templateEngine.process("mail/" + message.template(), context);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mime, MimeMessageHelper.MULTIPART_MODE_NO, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from(), properties.fromName());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(html, true);

            mailSender.send(mime);
            log.info("Email « {} » envoyé à {}", message.subject(), mask(message.to()));
        } catch (Exception e) {
            log.error("Échec d'envoi de l'email « {} » à {}", message.subject(), mask(message.to()), e);
        }
    }

    /** yassine@rh.com devient y***@rh.com : on ne met pas d'adresses complètes dans les logs. */
    private String mask(String email) {
        if (email == null) {
            return "?";
        }
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }
}