package com.hrapp.rh.utilisateur;

import com.hrapp.rh.common.exception.RequeteInvalideException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class JetonService {

    private static final String MESSAGE_LIEN_INVALIDE = "Ce lien est invalide ou a expiré";
    private static final Duration DELAI_ENTRE_DEUX_ENVOIS = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JetonUtilisateurRepository repository;
    private final long activationHours;
    private final long resetMinutes;

    public JetonService(JetonUtilisateurRepository repository,
                        @Value("${app.security.tokens.activation-hours:48}") long activationHours,
                        @Value("${app.security.tokens.reset-minutes:60}") long resetMinutes) {
        this.repository = repository;
        this.activationHours = activationHours;
        this.resetMinutes = resetMinutes;
    }

    public long getActivationHours() {
        return activationHours;
    }

    public long getResetMinutes() {
        return resetMinutes;
    }

    /**
     * Crée un jeton et renvoie sa valeur en clair (à mettre dans l'email, jamais ailleurs).
     * Les anciens jetons non utilisés du même type sont invalidés : un seul lien valable à la fois.
     */
    @Transactional
    public String issue(Utilisateur utilisateur, TypeJeton type) {
        LocalDateTime now = LocalDateTime.now();
        repository.invalidateUnused(utilisateur.getId(), type, now);

        byte[] bytes = new byte[32];                       // 256 bits d'aléatoire
        RANDOM.nextBytes(bytes);
        String jeton = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        JetonUtilisateur entity = new JetonUtilisateur();
        entity.setUtilisateur(utilisateur);
        entity.setTypeJeton(type);
        entity.setJetonHash(hash(jeton));                  // on ne stocke que l'empreinte
        entity.setDateCreation(now);
        entity.setDateExpiration(now.plus(validity(type)));
        repository.save(entity);
        return jeton;
    }

    /**
     * Vérifie et consomme un jeton : il doit exister, être du bon type, non utilisé et non expiré.
     * Toutes les causes d'échec donnent le même message (on ne révèle rien).
     */
    @Transactional
    public Utilisateur consume(String jeton, TypeJeton type) {
        LocalDateTime now = LocalDateTime.now();
        JetonUtilisateur entity = repository.findByJetonHash(hash(jeton.trim()))
                .filter(j -> j.getTypeJeton() == type)
                .filter(j -> j.getDateUtilisation() == null)
                .filter(j -> j.getDateExpiration().isAfter(now))
                .orElseThrow(() -> new RequeteInvalideException(MESSAGE_LIEN_INVALIDE));

        // Mise à jour atomique : si deux requêtes arrivent en même temps, une seule réussit
        if (repository.markUsed(entity.getId(), now) == 0) {
            throw new RequeteInvalideException(MESSAGE_LIEN_INVALIDE);
        }
        return entity.getUtilisateur();
    }

    /** Invalide tous les liens en attente de cet utilisateur (après un changement de mot de passe). */
    @Transactional
    public void invalidateAll(Utilisateur utilisateur) {
        repository.invalidateAllUnused(utilisateur.getId(), LocalDateTime.now());
    }

    /** Un jeton de ce type a-t-il déjà été créé il y a moins d'une minute ? (anti-spam) */
    @Transactional(readOnly = true)
    public boolean wasIssuedRecently(Utilisateur utilisateur, TypeJeton type) {
        return repository.existsByUtilisateurIdAndTypeJetonAndDateCreationAfter(
                utilisateur.getId(), type, LocalDateTime.now().minus(DELAI_ENTRE_DEUX_ENVOIS));
    }

    // ---------- méthodes privées ----------

    private Duration validity(TypeJeton type) {
        return type == TypeJeton.ACTIVATION ? Duration.ofHours(activationHours) : Duration.ofMinutes(resetMinutes);
    }

    private String hash(String jeton) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(jeton.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}