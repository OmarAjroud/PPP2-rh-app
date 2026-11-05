package com.hrapp.rh.securite;

import com.hrapp.rh.utilisateur.UtilisateurRepository;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Révocation des jetons.
 * <ul>
 *   <li>Un jeton de rafraîchissement ne sert qu'<b>une fois</b> (rotation) : il est révoqué dès qu'il est échangé.</li>
 *   <li>La déconnexion révoque le jeton de rafraîchissement présenté.</li>
 *   <li>« Fermer toutes les sessions » incrémente la version des jetons du compte : tous les jetons déjà émis,
 *       d'accès comme de rafraîchissement, sont refusés.</li>
 * </ul>
 * Les jetons d'accès (15 minutes) ne sont pas mémorisés : l'API reste sans état.
 */
@Slf4j
@Service
public class RevocationJetons {

    private final JetonRevoqueRepository repository;
    private final UtilisateurRepository utilisateurRepository;
    private final Clock clock;

    public RevocationJetons(JetonRevoqueRepository repository, UtilisateurRepository utilisateurRepository,
                            Clock clock) {
        this.repository = repository;
        this.utilisateurRepository = utilisateurRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(Claims jeton) {
        return jeton.getId() != null && repository.existsById(jeton.getId());
    }

    /** Révoque ce jeton (sans effet s'il l'est déjà). */
    @Transactional
    public void revoke(Claims jeton, Long utilisateurId) {
        if (jeton.getId() == null || repository.existsById(jeton.getId())) {
            return;
        }
        LocalDateTime expiration = LocalDateTime.ofInstant(jeton.getExpiration().toInstant(), ZoneId.systemDefault());
        repository.save(new JetonRevoque(jeton.getId(), utilisateurId, expiration));
    }

    /** Ferme toutes les sessions de ce compte : tous ses jetons déjà émis sont refusés. */
    @Transactional
    public void revokeAllSessions(Long utilisateurId) {
        utilisateurRepository.findById(utilisateurId)
                .ifPresent(u -> u.setVersionJeton(u.getVersionJeton() + 1));
    }

    /** Une fois expirés, les jetons sont refusés de toute façon : inutile de les garder. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpired() {
        int supprimes = repository.deleteExpired(LocalDateTime.now(clock));
        if (supprimes > 0) {
            log.info("{} jeton(s) révoqué(s) expiré(s) supprimé(s)", supprimes);
        }
    }
}
