package com.hrapp.rh.auth;

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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UtilisateurDetailsService userDetailsService;

    public AuthResponse login(LoginRequest request) {
        // vérifie le mot de passe (BCrypt) ; lève une exception si incorrect ou compte désactivé
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.nomUtilisateur(), request.motDePasse()));
        return buildResponse((UtilisateurConnecte) authentication.getPrincipal());
    }

    public AuthResponse refresh(RefreshRequest request) {
        Claims claims = jwtService.readClaims(request.refreshToken())
                .filter(jwtService::isRefreshToken)
                .orElseThrow(() -> new BadCredentialsException("Jeton de rafraîchissement invalide ou expiré"));

        UtilisateurConnecte utilisateur = userDetailsService.loadUserByUsername(claims.getSubject());
        if (!utilisateur.isEnabled()) {
            throw new DisabledException("Compte désactivé");
        }
        return buildResponse(utilisateur);
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