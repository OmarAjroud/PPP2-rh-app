package com.hrapp.rh.utilisateur;

import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.employe.EmployeRepository;
import com.hrapp.rh.mail.MailMessage;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
public class CompteService {

    private final UtilisateurRepository repository;
    private final EmployeRepository employeRepository;
    private final JetonService jetonService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendUrl;

    public CompteService(UtilisateurRepository repository,
                         EmployeRepository employeRepository,
                         JetonService jetonService,
                         PasswordEncoder passwordEncoder,
                         ApplicationEventPublisher eventPublisher,
                         @Value("${app.frontend-url}") String frontendUrl) {
        this.repository = repository;
        this.employeRepository = employeRepository;
        this.jetonService = jetonService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");   // sans « / » final
    }

    // ---------- côté RH / ADMIN ----------

    /** Compte d'un employé (404 s'il n'en a pas encore). */
    @Transactional(readOnly = true)
    public CompteResponse findAccount(Long employeId) {
        return repository.findByEmployeId(employeId)
                .map(this::toResponse)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun compte pour cet employé (id=" + employeId + ")"));
    }

    /**
     * Invite un employé : crée son compte (sans mot de passe) s'il n'existe pas,
     * puis envoie le lien d'activation. Si l'invitation est déjà partie, elle est renvoyée.
     */
    @Transactional
    public CompteResponse invite(UtilisateurConnecte acteur, Long employeId) {
        Employe employe = employeRepository.findById(employeId)
                .orElseThrow(() -> new RessourceIntrouvableException("Employé introuvable (id=" + employeId + ")"));
        verifyCanManage(acteur, employe);

        Utilisateur utilisateur = repository.findByEmployeId(employeId)
                .orElseGet(() -> createPendingAccount(employe));
        if (!utilisateur.isEnAttenteActivation()) {
            throw new ConflitException("Le compte de cet employé est déjà activé");
        }
        sendActivationEmail(utilisateur, employe);
        return toResponse(utilisateur);
    }

    // ---------- côté employé (endpoints publics) ----------

    /** L'employé choisit son mot de passe grâce au lien reçu : le compte devient actif. */
    @Transactional
    public void activate(ActivationRequest request) {
        Utilisateur utilisateur = jetonService.consume(request.jeton(), TypeJeton.ACTIVATION);
        utilisateur.setMotDePasseHash(passwordEncoder.encode(request.motDePasse()));
        utilisateur.setActif(true);
    }

    /**
     * Mot de passe oublié. Ne révèle JAMAIS si l'email existe : la réponse HTTP est la même dans tous les cas.
     * Compte en attente : on renvoie le lien d'activation ; compte actif : lien de réinitialisation ;
     * compte désactivé : rien.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        repository.findByEmployeEmailIgnoreCase(email.trim()).ifPresent(utilisateur -> {
            Employe employe = utilisateur.getEmploye();
            if (utilisateur.isEnAttenteActivation()) {
                if (!jetonService.wasIssuedRecently(utilisateur, TypeJeton.ACTIVATION)) {
                    sendActivationEmail(utilisateur, employe);
                }
            } else if (utilisateur.isActif()) {
                if (!jetonService.wasIssuedRecently(utilisateur, TypeJeton.REINITIALISATION)) {
                    sendResetEmail(utilisateur, employe);
                }
            }
        });
    }

    /** Nouveau mot de passe grâce au lien de réinitialisation. */
    @Transactional
    public void resetPassword(ReinitialisationRequest request) {
        Utilisateur utilisateur = jetonService.consume(request.jeton(), TypeJeton.REINITIALISATION);
        if (!utilisateur.isActif() || utilisateur.isEnAttenteActivation()) {
            throw new RequeteInvalideException("Ce lien est invalide ou a expiré");
        }
        applyNewPassword(utilisateur, request.nouveauMotDePasse());
    }

    /** Un utilisateur connecté change son propre mot de passe (il faut connaître l'actuel). */
    @Transactional
    public void changePassword(UtilisateurConnecte connecte, ChangementMotDePasseRequest request) {
        Utilisateur utilisateur = repository.findById(connecte.getUtilisateurId())
                .orElseThrow(() -> new RessourceIntrouvableException("Compte introuvable"));

        if (!passwordEncoder.matches(request.motDePasseActuel(), utilisateur.getMotDePasseHash())) {
            throw new RequeteInvalideException("Le mot de passe actuel est incorrect");
        }
        if (request.motDePasseActuel().equals(request.nouveauMotDePasse())) {
            throw new RequeteInvalideException("Le nouveau mot de passe doit être différent de l'ancien");
        }
        applyNewPassword(utilisateur, request.nouveauMotDePasse());
    }

    // ---------- méthodes privées ----------

    private Utilisateur createPendingAccount(Employe employe) {
        String nomUtilisateur = employe.getEmail().toLowerCase(Locale.ROOT);   // on se connecte avec son email
        if (repository.existsByNomUtilisateur(nomUtilisateur)) {
            throw new ConflitException("Le nom d'utilisateur « " + nomUtilisateur + " » est déjà pris");
        }
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmploye(employe);
        utilisateur.setNomUtilisateur(nomUtilisateur);
        utilisateur.setMotDePasseHash(null);
        utilisateur.setActif(false);
        return repository.save(utilisateur);
    }

    /** Enregistre le nouveau mot de passe, invalide tous les anciens jetons et prévient l'employé. */
    private void applyNewPassword(Utilisateur utilisateur, String nouveauMotDePasse) {
        utilisateur.setMotDePasseHash(passwordEncoder.encode(nouveauMotDePasse));
        utilisateur.setVersionJeton(utilisateur.getVersionJeton() + 1);   // tous les JWT émis avant deviennent invalides
        jetonService.invalidateAll(utilisateur);                          // et les liens email en attente aussi

        Employe employe = utilisateur.getEmploye();
        eventPublisher.publishEvent(new MailMessage(
                employe.getEmail(),
                "Votre mot de passe a été modifié – Application RH",
                "mot-de-passe-modifie",
                Map.of("prenom", employe.getPrenom())));
    }

    private void sendActivationEmail(Utilisateur utilisateur, Employe employe) {
        String jeton = jetonService.issue(utilisateur, TypeJeton.ACTIVATION);
        eventPublisher.publishEvent(new MailMessage(
                employe.getEmail(),
                "Activez votre compte – Application RH",
                "activation",
                Map.of("prenom", employe.getPrenom(),
                        "lien", frontendUrl + "/activer-compte?jeton=" + jeton,
                        "validiteHeures", jetonService.getActivationHours())));
    }

    private void sendResetEmail(Utilisateur utilisateur, Employe employe) {
        String jeton = jetonService.issue(utilisateur, TypeJeton.REINITIALISATION);
        eventPublisher.publishEvent(new MailMessage(
                employe.getEmail(),
                "Réinitialisation de votre mot de passe – Application RH",
                "reinitialisation",
                Map.of("prenom", employe.getPrenom(),
                        "lien", frontendUrl + "/reinitialiser-mot-de-passe?jeton=" + jeton,
                        "validiteMinutes", jetonService.getResetMinutes())));
    }

    /** Seul un ADMIN peut gérer le compte d'un ADMIN (sinon le RH pourrait prendre le contrôle). */
    private void verifyCanManage(UtilisateurConnecte acteur, Employe cible) {
        if (Roles.ADMIN.equals(cible.getRole().getNom()) && !acteur.hasRole(Roles.ADMIN)) {
            throw new AccessDeniedException("Seul un administrateur peut gérer le compte d'un ADMIN");
        }
    }

    private CompteResponse toResponse(Utilisateur utilisateur) {
        return new CompteResponse(utilisateur.getId(), utilisateur.getEmploye().getId(),
                utilisateur.getNomUtilisateur(), utilisateur.statut());
    }
}