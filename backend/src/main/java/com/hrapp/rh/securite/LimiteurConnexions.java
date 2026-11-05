package com.hrapp.rh.securite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Freine la recherche de mots de passe par essais successifs, avec deux limites sur les échecs de connexion :
 * <ul>
 *   <li><b>même compte, même adresse IP</b> (5 en 15 min par défaut) : arrête les essais sur un compte, sans
 *       permettre à un inconnu de bloquer un utilisateur réel depuis une autre adresse ;</li>
 *   <li><b>même adresse IP, tous comptes confondus</b> (20 en 15 min) : arrête le « password spraying »
 *       (un mot de passe courant essayé sur beaucoup de comptes).</li>
 * </ul>
 * Mémoire locale : valable pour une seule instance de l'application.
 */
@Component
public class LimiteurConnexions {

    private final FenetreGlissante parCompte;
    private final FenetreGlissante parAdresse;

    public LimiteurConnexions(@Value("${app.security.login.max-failures:5}") int maxEchecs,
                              @Value("${app.security.login.max-failures-per-ip:20}") int maxEchecsParAdresse,
                              @Value("${app.security.login.window-minutes:15}") long fenetreMinutes,
                              Clock clock) {
        Duration fenetre = Duration.ofMinutes(fenetreMinutes);
        this.parCompte = new FenetreGlissante(maxEchecs, fenetre, clock);
        this.parAdresse = new FenetreGlissante(maxEchecsParAdresse, fenetre, clock);
    }

    /** Durée d'attente restante si ce compte (depuis cette adresse) ou cette adresse est bloqué, sinon vide. */
    public Optional<Duration> blockedFor(String nomUtilisateur) {
        String ip = AdresseClient.courante();
        return Stream.of(parCompte.blockedFor(key(nomUtilisateur, ip)), parAdresse.blockedFor(ip))
                .flatMap(Optional::stream)
                .max(Duration::compareTo);
    }

    public void recordFailure(String nomUtilisateur) {
        String ip = AdresseClient.courante();
        parCompte.record(key(nomUtilisateur, ip));
        parAdresse.record(ip);
    }

    /** Connexion réussie : le compteur de ce compte (depuis cette adresse) repart à zéro. */
    public void recordSuccess(String nomUtilisateur) {
        parCompte.clear(key(nomUtilisateur, AdresseClient.courante()));
    }

    @Scheduled(fixedRate = 300_000)
    public void purgeExpired() {
        parCompte.purgeExpired();
        parAdresse.purgeExpired();
    }

    /** Oublie tous les compteurs (tests d'intégration : chaque test repart de zéro, comme la base). */
    public void reset() {
        parCompte.clearAll();
        parAdresse.clearAll();
    }

    int trackedKeys() {
        return parCompte.size();
    }

    private static String key(String nomUtilisateur, String ip) {
        String compte = nomUtilisateur == null ? "" : nomUtilisateur.trim().toLowerCase(Locale.ROOT);
        return compte + "|" + ip;
    }
}
