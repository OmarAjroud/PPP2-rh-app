package com.hrapp.rh.mail;

import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/mail")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MailTestController {

    private final MailAdminService service;

    /** 202 Accepted : l'envoi est asynchrone, on confirme seulement qu'il est lancé. */
    @PostMapping("/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> sendTest(@AuthenticationPrincipal UtilisateurConnecte admin) {
        String adresse = service.sendTestEmail(admin);
        return Map.of("message", "Email de test en cours d'envoi à " + adresse);
    }
}