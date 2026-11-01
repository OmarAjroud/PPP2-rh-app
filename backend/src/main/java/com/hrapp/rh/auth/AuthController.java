package com.hrapp.rh.auth;

import com.hrapp.rh.securite.UtilisateurConnecte;
import com.hrapp.rh.utilisateur.ActivationRequest;
import com.hrapp.rh.utilisateur.ChangementMotDePasseRequest;
import com.hrapp.rh.utilisateur.CompteService;
import com.hrapp.rh.utilisateur.MotDePasseOublieRequest;
import com.hrapp.rh.utilisateur.ReinitialisationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;
    private final CompteService compteService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return service.refresh(request);
    }

    /**
     * Déconnexion : l'API est stateless, il n'y a rien à supprimer côté serveur.
     * Le client (Angular) doit simplement oublier ses deux jetons.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
    }

    @GetMapping("/me")
    public ProfilResponse me(@AuthenticationPrincipal UtilisateurConnecte utilisateur) {
        return service.toProfile(utilisateur);
    }

    // ---------- endpoints publics (liens reçus par email) ----------

    /** L'employé choisit son mot de passe et active son compte. */
    @PostMapping("/activation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@Valid @RequestBody ActivationRequest request) {
        compteService.activate(request);
    }

    /** Même réponse que l'email existe ou non (on ne révèle pas quels comptes existent). */
    @PostMapping("/mot-de-passe-oublie")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> forgotPassword(@Valid @RequestBody MotDePasseOublieRequest request) {
        compteService.requestPasswordReset(request.email());
        return Map.of("message", "Si un compte correspond à cette adresse, un email vient d'être envoyé.");
    }

    @PostMapping("/reinitialisation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ReinitialisationRequest request) {
        compteService.resetPassword(request);
    }

    // ---------- utilisateur connecté ----------

    /** Change mon mot de passe : les autres sessions sont fermées, celle-ci reçoit de nouveaux jetons. */
    @PutMapping("/mot-de-passe")
    public AuthResponse changePassword(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                                       @Valid @RequestBody ChangementMotDePasseRequest request) {
        compteService.changePassword(utilisateur, request);
        return service.issueTokens(utilisateur.getUsername());
    }
}