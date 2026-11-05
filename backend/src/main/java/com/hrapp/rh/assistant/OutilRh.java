package com.hrapp.rh.assistant;

import java.util.List;
import java.util.Map;

/**
 * Description d'un outil de l'assistant RH, commune au chat (API Claude) et au serveur MCP.
 *
 * @param proprietes schéma JSON de chaque paramètre (ex. {"type": "integer", "description": "..."})
 * @param obligatoires paramètres requis
 */
public record OutilRh(String nom, String description, Map<String, Map<String, Object>> proprietes,
                      List<String> obligatoires) {

    public OutilRh {
        proprietes = Map.copyOf(proprietes);
        obligatoires = List.copyOf(obligatoires);
    }

    static Map<String, Object> entier(String description) {
        return Map.of("type", "integer", "description", description);
    }

    static Map<String, Object> texte(String description) {
        return Map.of("type", "string", "description", description);
    }

    static Map<String, Object> date(String description) {
        return Map.of("type", "string", "format", "date", "description", description + " (AAAA-MM-JJ)");
    }
}
