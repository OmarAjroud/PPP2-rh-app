package com.hrapp.rh.auth;

import com.hrapp.rh.audit.AuditAction;
import com.hrapp.rh.audit.AuditService;
import com.hrapp.rh.securite.LimiteurConnexions;
import com.hrapp.rh.securite.JwtService;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import com.hrapp.rh.securite.UtilisateurDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private UtilisateurDetailsService userDetailsService;
    @Mock private AuditService auditService;
    @Mock private LimiteurConnexions limiteur;

    @InjectMocks private AuthService service;

    @Test
    @DisplayName("Connexion réussie : enregistrée dans le journal au nom de l'utilisateur")
    void login_shouldRecordSuccessfulLogin() {
        UtilisateurConnecte admin = new UtilisateurConnecte(1L, 1L, 1L, "admin", "hash", Roles.ADMIN, true);
        given(authenticationManager.authenticate(any(Authentication.class)))
                .willReturn(new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));

        service.login(new LoginRequest("admin", "Test1234!"));

        verify(auditService).recordAs(eq(1L), eq("admin"), eq(AuditAction.CONNEXION),
                eq("Utilisateur"), eq(1L), isNull());
    }

    @Test
    @DisplayName("Connexion échouée : enregistrée (sans le mot de passe) puis l'erreur est relancée")
    void login_shouldRecordFailureAndRethrow_whenCredentialsAreWrong() {
        given(authenticationManager.authenticate(any(Authentication.class)))
                .willThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "mauvais-mot-de-passe")))
                .isInstanceOf(BadCredentialsException.class);

        verify(auditService).recordAs(isNull(), eq("admin"), eq(AuditAction.ECHEC_CONNEXION),
                eq("Utilisateur"), isNull(), isNull());
    }

    @Test
    @DisplayName("Connexion échouée : un compte non activé donne la même erreur qu'un mauvais mot de passe")
    void login_shouldHideDisabledAccount_behindTheSameError() {
        given(authenticationManager.authenticate(any(Authentication.class)))
                .willThrow(new DisabledException("User is disabled"));

        assertThatThrownBy(() -> service.login(new LoginRequest("yassine@rh.com", "Welcome123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Identifiants invalides");

        verify(auditService).recordAs(isNull(), eq("yassine@rh.com"), eq(AuditAction.ECHEC_CONNEXION),
                eq("Utilisateur"), isNull(), isNull());
    }
}