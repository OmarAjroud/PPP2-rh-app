package com.hrapp.rh.auth;

import com.hrapp.rh.audit.AuditAction;
import com.hrapp.rh.audit.AuditService;
import com.hrapp.rh.securite.LimiteurConnexions;
import com.hrapp.rh.securite.RevocationJetons;
import com.hrapp.rh.securite.JwtService;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import com.hrapp.rh.securite.UtilisateurDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private UtilisateurDetailsService userDetailsService;
    @Mock private AuditService auditService;
    @Mock private LimiteurConnexions limiteur;
    @Mock private RevocationJetons revocation;

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

    // ---------- rotation des jetons de rafraîchissement ----------

    private final UtilisateurConnecte lina = new UtilisateurConnecte(4L, 4L, 1L, "employe", "hash", Roles.EMPLOYE, true);

    @Test
    @DisplayName("Rafraîchissement : le jeton utilisé est révoqué (usage unique) et une nouvelle paire est émise")
    void refresh_shouldRevokeUsedToken_andIssueNewPair() {
        Claims claims = refreshClaims();
        given(userDetailsService.loadUserByUsername("employe")).willReturn(lina);
        given(revocation.isRevoked(claims)).willReturn(false);
        given(jwtService.generateAccessToken(lina)).willReturn("acces-2");
        given(jwtService.generateRefreshToken(lina)).willReturn("rafraichissement-2");

        AuthResponse reponse = service.refresh(new RefreshRequest("rafraichissement-1"));

        verify(revocation).revoke(claims, 4L);
        assertThat(reponse.accessToken()).isEqualTo("acces-2");
        assertThat(reponse.refreshToken()).isEqualTo("rafraichissement-2");
    }

    @Test
    @DisplayName("Rafraîchissement avec un jeton déjà utilisé : vol probable, toutes les sessions sont fermées")
    void refresh_withReusedToken_shouldCloseAllSessions() {
        Claims claims = refreshClaims();
        given(userDetailsService.loadUserByUsername("employe")).willReturn(lina);
        given(revocation.isRevoked(claims)).willReturn(true);

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("rafraichissement-1")))
                .isInstanceOf(BadCredentialsException.class);

        verify(revocation).revokeAllSessions(4L);
        verify(revocation, never()).revoke(any(), any());
        verify(jwtService, never()).generateAccessToken(any());
        verify(auditService).recordAs(eq(4L), eq("employe"), eq(AuditAction.DECONNEXION), eq("Utilisateur"), eq(4L),
                contains("vol possible"));
    }

    @Test
    @DisplayName("Déconnexion : le jeton de rafraîchissement présenté est révoqué ; sans jeton, rien à faire")
    void logout_shouldRevokeRefreshToken() {
        Claims claims = refreshClaims();
        given(userDetailsService.loadUserByUsername("employe")).willReturn(lina);

        service.logout("rafraichissement-1");
        service.logout(null);

        verify(revocation).revoke(claims, 4L);
    }

    private Claims refreshClaims() {
        Claims claims = Jwts.claims().subject("employe").id("jti-1").build();
        given(jwtService.readClaims("rafraichissement-1")).willReturn(Optional.of(claims));
        given(jwtService.isRefreshToken(claims)).willReturn(true);
        lenient().when(jwtService.matchesVersion(claims, lina)).thenReturn(true);
        return claims;
    }
}