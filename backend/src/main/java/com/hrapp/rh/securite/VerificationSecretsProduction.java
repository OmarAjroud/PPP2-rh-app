package com.hrapp.rh.securite;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * En production, refuse de démarrer avec un secret de développement : un JWT signé avec un secret
 * connu (il est dans le dépôt Git) peut être fabriqué par n'importe qui, pour n'importe quel compte.
 */
@Slf4j
@Component
@Profile("prod")
public class VerificationSecretsProduction implements InitializingBean {

    static final String MARQUEUR_DEV = "change-me";

    private final String jwtSecret;
    private final boolean comptesDeTest;

    public VerificationSecretsProduction(@Value("${app.security.jwt.secret}") String jwtSecret,
                                         @Value("${app.seed.test-accounts:false}") boolean comptesDeTest) {
        this.jwtSecret = jwtSecret;
        this.comptesDeTest = comptesDeTest;
    }

    @Override
    public void afterPropertiesSet() {
        if (jwtSecret == null || jwtSecret.contains(MARQUEUR_DEV) || jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET doit être un secret propre à ce déploiement "
                    + "(au moins 32 caractères aléatoires, ex. « openssl rand -base64 48 »), pas la valeur de développement");
        }
        if (comptesDeTest) {
            log.warn("Profil prod avec les comptes de test activés (SEED_TEST_ACCOUNTS=true) : "
                    + "acceptable pour une démonstration, jamais pour une vraie production");
        }
    }
}
