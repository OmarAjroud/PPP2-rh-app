package com.hrapp.rh.common.exception;

import java.time.LocalDateTime;
import java.util.Map;

/** Format unique des erreurs renvoyées par l'API. */
public record ErreurApi(
        LocalDateTime horodatage,
        int statut,
        String erreur,
        String message,
        String chemin,
        Map<String, String> erreursChamps
) {}