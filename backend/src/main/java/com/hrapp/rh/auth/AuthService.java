package com.hrapp.rh.auth;

import com.hrapp.rh.audit.AuditAction;
import com.hrapp.rh.audit.AuditService;
import com.hrapp.rh.securite.JwtService;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ENTITE = "Utilisateur";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UtilisateurDetailsService userDetailsService;
    private final AuditService auditService;

    public AuthResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.nomUtilisateur(), request.motDePasse()));
        } catch (AuthenticationException e) {
            auditService.recordAs(null, request.nomUtilisateur(), AuditAction.ECHEC_CONNEXION, ENTITE, null, null);
            if (e instanceof DisabledException) {
                // Compte non activé ou désactivé : même réponse qu'un mauvais mot de passe,
                // pour ne pas révéler qu'un compte existe.
                throw new BadCredentialsException("Identifiants invalides");
            }
            throw e;
        }
        UtilisateurConnecte utilisateur = (UtilisateurConnecte) authentication.getPrincipal();
        auditService.recordAs(utilisateur.getUtilisateurId(), utilisateur.getUsername(),
                AuditAction.CONNEXION, ENTITE, utilisateur.getUtilisateurId(), null);
        return buildResponse(utilisateur);
    }

    public AuthResponse refresh(RefreshRequest request) {
        Claims claims = jwtService.readClaims(request.refreshToken())
                .filter(jwtService::isRefreshToken)
                .orElseThrow(() -> new BadCredentialsException("Jeton de rafraîchissement invalide ou expiré"));

        UtilisateurConnecte utilisateur = userDetailsService.loadUserByUsername(claims.getSubject());
        if (!utilisateur.isEnabled() || !jwtService.matchesVersion(claims, utilisateur)) {
            throw new BadCredentialsException("Jeton de rafraîchissement invalide ou expiré");
        }
        return buildResponse(utilisateur);
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