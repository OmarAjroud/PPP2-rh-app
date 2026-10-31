package com.hrapp.rh.mail;

import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.employe.EmployeRepository;
import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MailAdminService {

    private final EmployeRepository employeRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Envoie un email de test à l'adresse de l'administrateur connecté ; renvoie cette adresse. */
    public String sendTestEmail(UtilisateurConnecte admin) {
        Employe employe = employeRepository.findById(admin.getEmployeId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Employé introuvable (id=" + admin.getEmployeId() + ")"));
        eventPublisher.publishEvent(new MailMessage(
                employe.getEmail(),
                "Test d'envoi – Application RH",
                "test",
                Map.of("prenom", employe.getPrenom())));
        return employe.getEmail();
    }
}