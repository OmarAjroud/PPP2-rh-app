package com.hrapp.rh.securite;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Utilisateur authentifié, disponible dans les contrôleurs via @AuthenticationPrincipal. */
@Getter
@AllArgsConstructor
public class UtilisateurConnecte implements UserDetails {

    private final Long utilisateurId;
    private final Long employeId;
    private final Long departementId;
    private final String nomUtilisateur;
    private final String motDePasseHash;
    private final String role;
    private final boolean actif;
    private final int versionJeton;

    /** Constructeur sans version (version 0) : pratique pour les tests. */
    public UtilisateurConnecte(Long utilisateurId, Long employeId, Long departementId, String nomUtilisateur,
                               String motDePasseHash, String role, boolean actif) {
        this(utilisateurId, employeId, departementId, nomUtilisateur, motDePasseHash, role, actif, 0);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring attend le préfixe ROLE_ pour hasRole('ADMIN')
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return motDePasseHash;
    }

    @Override
    public String getUsername() {
        return nomUtilisateur;
    }

    @Override
    public boolean isEnabled() {
        return actif;
    }

    public boolean hasRole(String nomRole) {
        return role.equals(nomRole);
    }

    public boolean isAdminOrRh() {
        return hasRole(Roles.ADMIN) || hasRole(Roles.RH);
    }
}