package com.hrapp.rh.assistant;

import com.hrapp.rh.common.exception.ServiceIndisponibleException;
import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Moteur local : un modèle servi par Ollama (gratuit, sans clé, les données ne quittent pas la machine).
 * API utilisée : POST /api/chat avec la liste des outils ; le modèle répond soit par du texte,
 * soit par des appels d'outils (message.tool_calls) dont on renvoie les résultats (messages « tool »).
 */
@Slf4j
@Component
public class OllamaAssistant implements ModeleAssistant {

    /** Un modèle « qui réfléchit » peut écrire son raisonnement avant la réponse : on ne garde que la réponse. */
    private static final Pattern RAISONNEMENT = Pattern.compile("(?s)^.*?</think>\\s*");

    private final RestClient http;
    private final OutilsRhService outils;
    private final AssistantProperties.Ollama config;
    private final int maxIterations;
    private final List<Map<String, Object>> definitionsOutils;

    @Autowired
    public OllamaAssistant(OutilsRhService outils, AssistantProperties properties) {
        this(RestClient.builder().requestFactory(requestFactory(properties.ollama())), outils, properties);
    }

    /** Pour les tests : un RestClient.Builder relié à un faux serveur. */
    OllamaAssistant(RestClient.Builder builder, OutilsRhService outils, AssistantProperties properties) {
        this.config = properties.ollama();
        this.maxIterations = properties.maxIterations();
        this.outils = outils;
        this.http = builder.baseUrl(config.url()).build();
        this.definitionsOutils = outils.definitions().stream().map(OllamaAssistant::toOllamaTool).toList();
    }

    private static SimpleClientHttpRequestFactory requestFactory(AssistantProperties.Ollama config) {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(Duration.ofSeconds(5));
        fabrique.setReadTimeout(Duration.ofSeconds(config.timeoutSeconds()));   // sur processeur, une réponse peut prendre du temps
        return fabrique;
    }

    @Override
    public String nom() {
        return AssistantProperties.OLLAMA;
    }

    @Override
    public ChatResponse repondre(UtilisateurConnecte utilisateur, String consignes, List<ChatRequest.Message> conversation) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", consignes));
        for (ChatRequest.Message m : conversation) {
            messages.add(Map.of("role", m.deLUtilisateur() ? "user" : "assistant", "content", m.contenu()));
        }

        List<String> outilsUtilises = new ArrayList<>();
        for (int tour = 0; tour < maxIterations; tour++) {
            Map<String, Object> message = message(call(messages));
            List<Map<String, Object>> appels = toolCalls(message);
            if (appels.isEmpty()) {
                String texte = clean(String.valueOf(message.getOrDefault("content", "")));
                return new ChatResponse(texte.isBlank() ? "Je n'ai pas de réponse à proposer." : texte, outilsUtilises);
            }
            messages.add(message);                                       // l'assistant et ses appels d'outils
            for (Map<String, Object> appel : appels) {
                Map<String, Object> fonction = asMap(appel.get("function"));
                String nomOutil = String.valueOf(fonction.get("name"));
                outilsUtilises.add(nomOutil);
                ResultatOutil resultat = outils.execute(utilisateur, nomOutil, asMap(fonction.get("arguments")),
                        OutilsRhService.Source.CHAT);
                messages.add(Map.of("role", "tool", "tool_name", nomOutil,
                        "content", resultat.erreur() ? "ERREUR : " + resultat.contenu() : resultat.contenu()));
            }
        }
        return new ChatResponse("Cette question demande trop d'étapes : pouvez-vous la préciser ou la découper ?",
                outilsUtilises);
    }

    @Override
    public Map<String, Object> statut() {
        Map<String, Object> statut = new LinkedHashMap<>();
        statut.put("moteur", nom());
        statut.put("modele", config.model());
        try {
            Map<String, Object> tags = http.get().uri("/api/tags").retrieve().body(MAP);
            boolean installe = tags != null && asList(tags.get("models")).stream()
                    .map(m -> String.valueOf(asMap(m).get("name")))
                    .anyMatch(nomModele -> nomModele.equals(config.model())
                            || nomModele.equals(config.model() + ":latest"));
            statut.put("disponible", installe);
            if (!installe) {
                statut.put("message", "Modèle non installé : lancez « ollama pull " + config.model() + " »");
            }
        } catch (RestClientException e) {
            statut.put("disponible", false);
            statut.put("message", "Ollama ne répond pas sur " + config.url() + " : est-il démarré ?");
        }
        return statut;
    }

    /**
     * Charge le modèle en mémoire dès le démarrage de l'application : sinon la première question
     * (souvent pendant une démonstration) attend le chargement, jusqu'à 30 secondes sur processeur.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void preload() {
        try {
            http.post().uri("/api/generate").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", config.model(), "keep_alive", config.keepAlive()))
                    .retrieve().toBodilessEntity();
            log.info("Assistant RH : modèle {} chargé par Ollama", config.model());
        } catch (RestClientException e) {
            log.warn("Assistant RH : impossible de précharger {} sur {} ({}). Le chat sera indisponible "
                    + "tant qu'Ollama n'est pas démarré et le modèle installé.", config.model(), config.url(), e.getMessage());
        }
    }

    // ---------- méthodes privées ----------

    private Map<String, Object> call(List<Map<String, Object>> messages) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("model", config.model());
        corps.put("messages", messages);
        corps.put("tools", definitionsOutils);
        corps.put("stream", false);
        corps.put("keep_alive", config.keepAlive());
        corps.put("options", Map.of("num_ctx", config.numCtx(), "temperature", 0.2));   // réponses factuelles, peu de fantaisie
        try {
            Map<String, Object> reponse = http.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON)
                    .body(corps).retrieve().body(MAP);
            if (reponse == null) {
                throw new ServiceIndisponibleException("Réponse vide du modèle local");
            }
            return reponse;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(404))) {
                throw new ServiceIndisponibleException(
                        "Le modèle « " + config.model() + " » n'est pas installé : lancez « ollama pull " + config.model() + " »");
            }
            log.error("Assistant RH : erreur d'Ollama (HTTP {}) : {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ServiceIndisponibleException("L'assistant est momentanément indisponible");
        } catch (ResourceAccessException e) {
            log.warn("Assistant RH : Ollama injoignable sur {} ({})", config.url(), e.getMessage());
            throw new ServiceIndisponibleException(
                    "L'assistant local ne répond pas : vérifiez qu'Ollama est démarré (" + config.url() + ")");
        }
    }

    private static Map<String, Object> message(Map<String, Object> reponse) {
        return asMap(reponse.get("message"));
    }

    private static List<Map<String, Object>> toolCalls(Map<String, Object> message) {
        return asList(message.get("tool_calls")).stream().map(OllamaAssistant::asMap).toList();
    }

    static String clean(String texte) {
        return RAISONNEMENT.matcher(texte).replaceFirst("").trim();
    }

    private static Map<String, Object> toOllamaTool(OutilRh outil) {
        return Map.of("type", "function", "function", Map.of(
                "name", outil.nom(),
                "description", outil.description(),
                "parameters", Map.of("type", "object", "properties", outil.proprietes(),
                        "required", outil.obligatoires())));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object valeur) {
        return valeur instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static List<?> asList(Object valeur) {
        return valeur instanceof List<?> l ? l : List.of();
    }

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};
}
