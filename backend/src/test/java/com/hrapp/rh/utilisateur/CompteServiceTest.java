package com.hrapp.rh.utilisateur;

import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.employe.EmployeRepository;
import com.hrapp.rh.mail.MailMessage;
import com.hrapp.rh.role.Role;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CompteServiceTest {

    @Mock private UtilisateurRepository repository;
    @Mock private EmployeRepository employeRepository;
    @Mock private JetonService jetonService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CompteService service;

    @BeforeEach
    void setUp() {
        // l'adresse se termine par « / » exprès : le service doit l'enlever
        service = new CompteService(repository, employeRepository, jetonService, passwordEncoder,
                eventPublisher, "http://localhost:4200/");
    }

    // ---------- invitation (RH / ADMIN) ----------

    @Test
    @DisplayName("Invitation : 404 si l'employé n'existe pas")
    void invite_shouldThrowNotFound_whenEmployeeDoesNotExist() {
        given(employeRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.invite(user(2L, Roles.RH), 99L))
                .isInstanceOf(RessourceIntrouvableException.class);

        verifyNoInteractions(repository, jetonService, eventPublisher);
    }

    @Test
    @DisplayName("Invitation : le RH ne peut pas inviter un ADMIN")
    void invite_shouldThrowAccessDenied_whenRhTargetsAnAdmin() {
        given(employeRepository.findById(1L)).willReturn(Optional.of(employe(1L, Roles.ADMIN)));

        assertThatThrownBy(() -> service.invite(user(2L, Roles.RH), 1L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository, jetonService, eventPublisher);
    }

    @Test
    @DisplayName("Invitation : crée un compte en attente (sans mot de passe) et envoie l'email d'activation")
    void invite_shouldCreatePendingAccountAndSendEmail_whenNoAccountYet() {
        given(employeRepository.findById(4L)).willReturn(Optional.of(employe(4L, Roles.EMPLOYE)));
        given(repository.findByEmployeId(4L)).willReturn(Optional.empty());
        given(repository.existsByNomUtilisateur("employe@rh.com")).willReturn(false);
        given(repository.save(any(Utilisateur.class))).willAnswer(invocation -> {
            Utilisateur saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        given(jetonService.issue(any(Utilisateur.class), eq(TypeJeton.ACTIVATION))).willReturn("RAWTOKEN");

        CompteResponse result = service.invite(user(2L, Roles.RH), 4L);

        assertThat(result.statut()).isEqualTo(StatutCompte.EN_ATTENTE);
        assertThat(result.nomUtilisateur()).isEqualTo("employe@rh.com");

        ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMotDePasseHash()).isNull();
        assertThat(captor.getValue().isActif()).isFalse();

        MailMessage mail = captureMail();
        assertThat(mail.to()).isEqualTo("employe@rh.com");
        assertThat(mail.template()).isEqualTo("activation");
        assertThat(mail.variables())
                .containsEntry("lien", "http://localhost:4200/activer-compte?jeton=RAWTOKEN")
                .containsEntry("prenom", "Lina");
    }

    @Test
    @DisplayName("Invitation : renvoie l'email sans recréer le compte si l'invitation est déjà partie")
    void invite_shouldResendEmail_whenAccountIsStillPending() {
        given(employeRepository.findById(4L)).willReturn(Optional.of(employe(4L, Roles.EMPLOYE)));
        given(repository.findByEmployeId(4L))
                .willReturn(Optional.of(compte(10L, employe(4L, Roles.EMPLOYE), null, false)));
        given(jetonService.issue(any(Utilisateur.class), eq(TypeJeton.ACTIVATION))).willReturn("NEWTOKEN");

        service.invite(user(2L, Roles.RH), 4L);

        verify(repository, never()).save(any());
        assertThat(captureMail().variables())
                .containsEntry("lien", "http://localhost:4200/activer-compte?jeton=NEWTOKEN");
    }

    @Test
    @DisplayName("Invitation : refusée si le compte est déjà activé")
    void invite_shouldThrowConflict_whenAccountIsAlreadyActivated() {
        given(employeRepository.findById(4L)).willReturn(Optional.of(employe(4L, Roles.EMPLOYE)));
        given(repository.findByEmployeId(4L))
                .willReturn(Optional.of(compte(10L, employe(4L, Roles.EMPLOYE), "HASH", true)));

        assertThatThrownBy(() -> service.invite(user(2L, Roles.RH), 4L))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("déjà activé");

        verifyNoInteractions(jetonService, eventPublisher);
    }

    @Test
    @DisplayName("Invitation : refusée si le nom d'utilisateur est déjà pris")
    void invite_shouldThrowConflict_whenUsernameIsAlreadyTaken() {
        given(employeRepository.findById(4L)).willReturn(Optional.of(employe(4L, Roles.EMPLOYE)));
        given(repository.findByEmployeId(4L)).willReturn(Optional.empty());
        given(repository.existsByNomUtilisateur("employe@rh.com")).willReturn(true);

        assertThatThrownBy(() -> service.invite(user(2L, Roles.RH), 4L))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("déjà pris");

        verify(repository, never()).save(any());
    }

    // ---------- activation ----------

    @Test
    @DisplayName("Activation : enregistre le mot de passe chiffré et active le compte")
    void activate_shouldSetHashedPasswordAndEnableAccount() {
        Utilisateur compte = compte(10L, employe(4L, Roles.EMPLOYE), null, false);
        given(jetonService.consume("RAW", TypeJeton.ACTIVATION)).willReturn(compte);
        given(passwordEncoder.encode("Welcome123")).willReturn("HASH");

        service.activate(new ActivationRequest("RAW", "Welcome123"));

        assertThat(compte.getMotDePasseHash()).isEqualTo("HASH");
        assertThat(compte.isActif()).isTrue();
    }

    @Test
    @DisplayName("Activation : un lien invalide est refusé et aucun mot de passe n'est traité")
    void activate_shouldPropagateError_whenTokenIsInvalid() {
        given(jetonService.consume("BAD", TypeJeton.ACTIVATION))
                .willThrow(new RequeteInvalideException("Ce lien est invalide ou a expiré"));

        assertThatThrownBy(() -> service.activate(new ActivationRequest("BAD", "Welcome123")))
                .isInstanceOf(RequeteInvalideException.class);

        verifyNoInteractions(passwordEncoder);
    }

    // ---------- mot de passe oublié ----------

    @Test
    @DisplayName("Mot de passe oublié : un email inconnu ne déclenche rien (et ne le révèle pas)")
    void requestPasswordReset_shouldDoNothing_whenEmailIsUnknown() {
        given(repository.findByEmployeEmailIgnoreCase("inconnu@rh.com")).willReturn(Optional.empty());

        service.requestPasswordReset("inconnu@rh.com");

        verifyNoInteractions(jetonService, eventPublisher);
    }

    @Test
    @DisplayName("Mot de passe oublié : compte actif, envoi du lien de réinitialisation")
    void requestPasswordReset_shouldSendResetEmail_whenAccountIsActive() {
        Utilisateur compte = compte(10L, employe(4L, Roles.EMPLOYE), "HASH", true);
        given(repository.findByEmployeEmailIgnoreCase("employe@rh.com")).willReturn(Optional.of(compte));
        given(jetonService.wasIssuedRecently(any(Utilisateur.class), eq(TypeJeton.REINITIALISATION)))
                .willReturn(false);
        given(jetonService.issue(any(Utilisateur.class), eq(TypeJeton.REINITIALISATION)))
                .willReturn("RESETTOKEN");

        service.requestPasswordReset("employe@rh.com");

        MailMessage mail = captureMail();
        assertThat(mail.template()).isEqualTo("reinitialisation");
        assertThat(mail.variables())
                .containsEntry("lien", "http://localhost:4200/reinitialiser-mot-de-passe?jeton=RESETTOKEN");
    }

    @Test
    @DisplayName("Mot de passe oublié : compte jamais activé, on renvoie le lien d'activation")
    void requestPasswordReset_shouldSendActivationEmail_whenAccountIsPending() {
        Utilisateur compte = compte(10L, employe(4L, Roles.EMPLOYE), null, false);
        given(repository.findByEmployeEmailIgnoreCase("employe@rh.com")).willReturn(Optional.of(compte));
        given(jetonService.wasIssuedRecently(any(Utilisateur.class), eq(TypeJeton.ACTIVATION))).willReturn(false);
        given(jetonService.issue(any(Utilisateur.class), eq(TypeJeton.ACTIVATION))).willReturn("ACTTOKEN");

        service.requestPasswordReset("employe@rh.com");

        assertThat(captureMail().template()).isEqualTo("activation");
    }

    @Test
    @DisplayName("Mot de passe oublié : un compte désactivé ne reçoit rien")
    void requestPasswordReset_shouldDoNothing_whenAccountIsDisabled() {
        Utilisateur compte = compte(10L, employe(4L, Roles.EMPLOYE), "HASH", false);
        given(repository.findByEmployeEmailIgnoreCase("employe@rh.com")).willReturn(Optional.of(compte));

        service.requestPasswordReset("employe@rh.com");

        verify(jetonService, never()).issue(any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Mot de passe oublié : pas de second email dans la minute (anti-spam)")
    void requestPasswordReset_shouldNotSendAnotherEmail_whenOneWasJustSent() {
        Utilisateur compte = compte(10L, employe(4L, Roles.EMPLOYE), "HASH", true);
        given(repository.findByEmployeEmailIgnoreCase("employe@rh.com")).willReturn(Optional.of(compte));
        given(jetonService.wasIssuedRecently(any(Utilisateur.class), eq(TypeJeton.REINITIALISATION)))
                .willReturn(true);

        service.requestPasswordReset("employe@rh.com");

        verify(jetonService, never()).issue(any(), any());
        verifyNoInteractions(eventPublisher);
    }

    // ---------- réinitialisation ----------

    @Test
    @DisplayName("Réinitialisation : nouveau mot de passe, version incrémentée, anciens liens invalidés, email de prévention")
    void resetPassword_shouldUpdatePasswordBumpVersionAndRevokeTokens() {
        Utilisateur compte = compte(10L, employe(4L, Roles.EMPLOYE), "OLDHASH", true);
        given(jetonService.consume("RAW", TypeJeton.REINITIALISATION)).willReturn(compte);
        given(passwordEncoder.encode("NewPass123")).willReturn("NEWHASH");

        service.resetPassword(new ReinitialisationRequest("RAW", "NewPass123"));

        assertThat(compte.getMotDePasseHash()).isEqualTo("NEWHASH");
        assertThat(compte.getVersionJeton()).isEqualTo(1);
        verify(jetonService).invalidateAll(compte);
        assertThat(captureMail().template()).isEqualTo("mot-de-passe-modifie");
    }

    // ---------- changement de son propre mot de passe ----------

    @Test
    @DisplayName("Changement : refusé si le mot de passe actuel est incorrect")
    void changePassword_shouldThrowBadRequest_whenCurrentPasswordIsWrong() {
        given(repository.findById(10L))
                .willReturn(Optional.of(compte(10L, employe(4L, Roles.EMPLOYE), "OLDHASH", true)));
        given(passwordEncoder.matches("wrong", "OLDHASH")).willReturn(false);

        assertThatThrownBy(() -> service.changePassword(user(10L, Roles.EMPLOYE),
                new ChangementMotDePasseRequest("wrong", "NewPass123")))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("incorrect");

        verify(passwordEncoder, never()).encode(anyString());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Changement : refusé si le nouveau mot de passe est identique à l'ancien")
    void changePassword_shouldThrowBadRequest_whenNewPasswordEqualsCurrent() {
        given(repository.findById(10L))
                .willReturn(Optional.of(compte(10L, employe(4L, Roles.EMPLOYE), "OLDHASH", true)));
        given(passwordEncoder.matches("Same1234", "OLDHASH")).willReturn(true);

        assertThatThrownBy(() -> service.changePassword(user(10L, Roles.EMPLOYE),
                new ChangementMotDePasseRequest("Same1234", "Same1234")))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("différent");
    }

    @Test
    @DisplayName("Changement : met à jour le mot de passe, incrémente la version et prévient par email")
    void changePassword_shouldUpdatePasswordAndBumpVersion_whenCurrentPasswordIsCorrect() {
        Utilisateur compte = compte(10L, employe(4L, Roles.EMPLOYE), "OLDHASH", true);
        given(repository.findById(10L)).willReturn(Optional.of(compte));
        given(passwordEncoder.matches("Old12345", "OLDHASH")).willReturn(true);
        given(passwordEncoder.encode("New12345")).willReturn("NEWHASH");

        service.changePassword(user(10L, Roles.EMPLOYE), new ChangementMotDePasseRequest("Old12345", "New12345"));

        assertThat(compte.getMotDePasseHash()).isEqualTo("NEWHASH");
        assertThat(compte.getVersionJeton()).isEqualTo(1);
        verify(jetonService).invalidateAll(compte);
        assertThat(captureMail().template()).isEqualTo("mot-de-passe-modifie");
    }

    // ---------- méthodes utilitaires ----------

    private MailMessage captureMail() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return (MailMessage) captor.getValue();
    }

    private UtilisateurConnecte user(Long utilisateurId, String role) {
        return new UtilisateurConnecte(utilisateurId, 2L, 1L, "test", "hash", role, true);
    }

    private Employe employe(Long id, String nomRole) {
        Role role = new Role();
        role.setNom(nomRole);
        Employe e = new Employe();
        e.setId(id);
        e.setPrenom("Lina");
        e.setNom("Gharbi");
        e.setEmail("employe@rh.com");
        e.setRole(role);
        return e;
    }

    private Utilisateur compte(Long id, Employe employe, String hash, boolean actif) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        u.setEmploye(employe);
        u.setNomUtilisateur("compte" + id);
        u.setMotDePasseHash(hash);
        u.setActif(actif);
        return u;
    }
}