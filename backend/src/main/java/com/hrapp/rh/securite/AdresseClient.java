package com.hrapp.rh.securite;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Adresse IP du client de la requête en cours (tient compte de X-Forwarded-For si un proxy est déclaré). */
public final class AdresseClient {

    private AdresseClient() {}

    public static String courante() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributs) {
            return attributs.getRequest().getRemoteAddr();
        }
        return "local";
    }
}
