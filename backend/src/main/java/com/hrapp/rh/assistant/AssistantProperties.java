package com.hrapp.rh.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres de l'assistant RH (section app.assistant de application.yml).
 *
 * @param provider      moteur du chat : « ollama » (modèle local, gratuit, par défaut) ou « claude » (API, clé requise)
 * @param maxIterations nombre maximal d'allers-retours modèle / outils pour une question
 */
@ConfigurationProperties(prefix = "app.assistant")
public record AssistantProperties(String provider, Integer maxIterations, Ollama ollama, Claude claude) {

    public static final String OLLAMA = "ollama";
    public static final String CLAUDE = "claude";

    public AssistantProperties {
        provider = provider == null || provider.isBlank() ? OLLAMA : provider.trim().toLowerCase();
        maxIterations = maxIterations == null || maxIterations < 1 ? 6 : maxIterations;
        ollama = ollama == null ? new Ollama(null, null, null, null, null) : ollama;
        claude = claude == null ? new Claude(null, null, null) : claude;
    }

    /**
     * Modèle local servi par Ollama (https://ollama.com).
     *
     * @param url            adresse du serveur Ollama
     * @param model          modèle à utiliser (doit supporter l'appel d'outils et être installé : ollama pull ...)
     * @param keepAlive      durée pendant laquelle le modèle reste chargé en mémoire après une question
     * @param numCtx         taille du contexte (en jetons) : outils + conversation + résultats doivent y tenir
     * @param timeoutSeconds délai maximal d'une réponse (un modèle sur processeur, sans carte graphique, est lent)
     */
    public record Ollama(String url, String model, String keepAlive, Integer numCtx, Integer timeoutSeconds) {
        public Ollama {
            url = url == null || url.isBlank() ? "http://localhost:11434" : url.trim().replaceAll("/+$", "");
            model = model == null || model.isBlank() ? "qwen3:4b-instruct" : model.trim();
            keepAlive = keepAlive == null || keepAlive.isBlank() ? "30m" : keepAlive.trim();
            numCtx = numCtx == null || numCtx < 2048 ? 8192 : numCtx;
            timeoutSeconds = timeoutSeconds == null || timeoutSeconds < 10 ? 180 : timeoutSeconds;
        }
    }

    /**
     * API Claude (optionnelle).
     *
     * @param apiKey clé de l'API ; vide = moteur Claude indisponible
     * @param model  modèle Claude
     * @param effort effort de raisonnement : low, medium, high, xhigh, max
     */
    public record Claude(String apiKey, String model, String effort) {
        public Claude {
            apiKey = apiKey == null ? "" : apiKey.trim();
            model = model == null || model.isBlank() ? "claude-opus-5-5" : model.trim();
            effort = effort == null || effort.isBlank() ? "medium" : effort.trim().toLowerCase();
        }
    }
}
