package com.hrapp.rh.auth;

import com.hrapp.rh.audit.AuditAction;
import com.hrapp.rh.audit.AuditService;
import com.hrapp.rh.common.exception.TropDeTentativesException;
import com.hrapp.rh.securite.JwtService;
import com.hrapp.rh.securite.LimiteurConnexions;
import com.hrapp.rh.securite.RevocationJetons;
import com.hrapp.rh.securite.UtilisateurConnecte;
import com.hrapp.rh.securite.UtilisateurDetailsService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ENTITE = "Utilisateur";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UtilisateurDetailsService userDetailsService;
    private final AuditService auditService;
    private final LimiteurConnexions limiteur;
    private final RevocationJetons revocation;

    public AuthResponse login(LoginRequest request) {
        // Trop d'échecs récents pour ce compte depuis cette adresse : on ne vérifie même pas le mot de passe
        limiteur.blockedFor(request.nomUtilisateur()).ifPresent(attente -> {
            throw new TropDeTentativesException("Trop de tentatives de connexion : réessayez dans "
                    + Math.max(1, attente.toMinutes() + (attente.toSecondsPart() > 0 ? 1 : 0)) + " minute(s)", attente);
        });
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.nomUtilisateur(), request.motDePasse()));
        } catch (AuthenticationException e) {
            limiteur.recordFailure(request.nomUtilisateur());
            auditService.recordAs(null, request.nomUtilisateur(), AuditAction.ECHEC_CONNEXION, ENTITE, null, null);
            if (e instanceof DisabledException) {
                // Compte non activé ou désactivé : même réponse qu'un mauvais mot de passe,
                // pour ne pas révéler qu'un compte existe.
                throw new BadCredentialsException("Identifiants invalides");
            }
            throw e;
        }
        UtilisateurConnecte utilisateur = (UtilisateurConnecte) authentication.getPrincipal();
        limiteur.recordSuccess(request.nomUtilisateur());
        auditService.recordAs(utilisateur.getUtilisateurId(), utilisateur.getUsername(),
                AuditAction.CONNEXION, ENTITE, utilisateur.getUtilisateurId(), null);
        return buildResponse(utilisateur);
    }

    /**
     * Échange un jeton de rafraîchissement contre une nouvelle paire de jetons. Chaque jeton de rafraîchissement
     * ne sert qu'une fois (rotation) : s'il est présenté une seconde fois, il a forcément été copié (vol) et
     * toutes les sessions du compte sont fermées par précaution.
     */
    @Transactional(noRollbackFor = AuthenticationException.class)    // la fermeture des sessions doit être conservée
    public AuthResponse refresh(RefreshRequest request) {
        Claims claims = jwtService.readClaims(request.refreshToken())
                .filter(jwtService::isRefreshToken)
                .orElseThrow(() -> new BadCredentialsException("Jeton de rafraîchissement invalide ou expiré"));

        UtilisateurConnecte utilisateur = userDetailsService.loadUserByUsername(claims.getSubject());
        if (!utilisateur.isEnabled() || !jwtService.matchesVersion(claims, utilisateur)) {
            throw new BadCredentialsException("Jeton de rafraîchissement invalide ou expiré");
        }
        if (revocation.isRevoked(claims)) {
            revocation.revokeAllSessions(utilisateur.getUtilisateurId());
            auditService.recordAs(utilisateur.getUtilisateurId(), utilisateur.getUsername(), AuditAction.DECONNEXION,
                    ENTITE, utilisateur.getUtilisateurId(),
                    "Jeton de rafraîchissement réutilisé (vol possible) : toutes les sessions ont été fermées");
            throw new BadCredentialsException("Jeton de rafraîchissement invalide ou expiré");
        }
        revocation.revoke(claims, utilisateur.getUtilisateurId());          // usage unique
        return buildResponse(utilisateur);
    }

    /** Déconnexion : le jeton de rafraîchissement présenté est révoqué (le jeton d'accès expire seul, 15 min max). */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        jwtService.readClaims(refreshToken).filter(jwtService::isRefreshToken).ifPresent(claims -> {
            try {
                UtilisateurConnecte utilisateur = userDetailsService.loadUserByUsername(claims.getSubject());
                revocation.revoke(claims, utilisateur.getUtilisateurId());
                auditService.recordAs(utilisateur.getUtilisateurId(), utilisateur.getUsername(),
                        AuditAction.DECONNEXION, ENTITE, utilisateur.getUtilisateurId(), null);
            } catch (UsernameNotFoundException ignored) {
                // compte supprimé entre-temps : rien à révoquer
            }
        });
    }

    /** Ferme toutes mes sessions, sur tous mes appareils (ex. téléphone perdu). */
    @Transactional
    public void logoutEverywhere(UtilisateurConnecte utilisateur) {
        revocation.revokeAllSessions(utilisateur.getUtilisateurId());
        auditService.recordAs(utilisateur.getUtilisateurId(), utilisateur.getUsername(), AuditAction.DECONNEXION,
                ENTITE, utilisateur.getUtilisateurId(), "Toutes les sessions ont été fermées");
    }

    /** Émet de nouveaux jetons pour un utilisateur existant (après un changement de mot de passe). */
    public AuthResponse issueTokens(String nomUtilisateur) {
        return buildResponse(userDetailsService.loadUserByUsername(nomUtilisateur));
    }

    public ProfilResponse toProfile(UtilisateurConnecte utilisateur) {
        return new ProfilResponse(utilisateur.getNomUtilisateur(), utilisateur.getRole(),
                utilisateur.getEmployeId(), utilisateur.getDepartementId());
    }

    private AuthResponse buildResponse(UtilisateurConnecte utilisateur) {
        return new AuthResponse(
                jwtService.generateAccessToken(utilisateur),
                jwtService.generateRefreshToken(utilisateur),
                "Bearer",
                jwtService.getAccessValiditySeconds(),
                toProfile(utilisateur));
    }
}