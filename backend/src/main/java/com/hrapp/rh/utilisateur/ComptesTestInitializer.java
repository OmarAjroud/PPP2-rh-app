package com.hrapp.rh.utilisateur;

import com.hrapp.rh.employe.EmployeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Crée les comptes de test au démarrage (si app.seed.test-accounts=true). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.test-accounts", havingValue = "true")
public class ComptesTestInitializer implements ApplicationRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final EmployeRepository employeRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.test-password}")
    private String testPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createAccountIfMissing("admin", "admin@rh.com");
        createAccountIfMissing("rh", "rh@rh.com");
        createAccountIfMissing("manager", "manager@rh.com");
        createAccountIfMissing("employe", "employe@rh.com");
    }

    private void createAccountIfMissing(String nomUtilisateur, String emailEmploye) {
        if (utilisateurRepository.existsByNomUtilisateur(nomUtilisateur)) {
            return;
        }
        employeRepository.findByEmailIgnoreCase(emailEmploye).ifPresent(employe -> {
            Utilisateur utilisateur = new Utilisateur();
            utilisateur.setEmploye(employe);
            utilisateur.setNomUtilisateur(nomUtilisateur);
            utilisateur.setMotDePasseHash(passwordEncoder.encode(testPassword));
            utilisateur.setActif(true);
            utilisateurRepository.save(utilisateur);
            log.info("Compte de test créé : {}", nomUtilisateur);
        });
    }
}