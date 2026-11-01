package com.hrapp.rh.securite;

import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.utilisateur.Utilisateur;
import com.hrapp.rh.utilisateur.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UtilisateurDetailsService implements UserDetailsService {

    private final UtilisateurRepository repository;

    @Override
    @Transactional(readOnly = true)
    public UtilisateurConnecte loadUserByUsername(String nomUtilisateur) throws UsernameNotFoundException {
        Utilisateur utilisateur = repository.findByNomUtilisateur(nomUtilisateur)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + nomUtilisateur));
        Employe employe = utilisateur.getEmploye();
        return new UtilisateurConnecte(
                utilisateur.getId(),
                employe.getId(),
                employe.getDepartement().getId(),
                utilisateur.getNomUtilisateur(),
                utilisateur.getMotDePasseHash(),
                employe.getRole().getNom(),
                utilisateur.isActif(),
                utilisateur.getVersionJeton());
    }
}