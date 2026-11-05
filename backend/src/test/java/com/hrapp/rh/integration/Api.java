package com.hrapp.rh.integration;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/** Petit client HTTP pour les tests : ne lève jamais sur un code d'erreur, renvoie le statut et le JSON. */
public final class Api {

    public static final String MOT_DE_PASSE = "Test1234!";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient http;

    Api(String baseUrl) {
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultStatusHandler(status -> true, (requete, reponse) -> { })   // on lit le statut nous-mêmes
                .build();
    }

    public record Reponse(int statut, JsonNode json) {
        public JsonNode get(String champ) {
            return json.path(champ);
        }
    }

    /** Se connecte avec un compte de test (admin, rh, manager, employe) et renvoie le jeton d'accès. */
    public String login(String nomUtilisateur) {
        Reponse reponse = post("/api/auth/login", null,
                Map.of("nomUtilisateur", nomUtilisateur, "motDePasse", MOT_DE_PASSE));
        if (reponse.statut() != 200) {
            throw new IllegalStateException("Connexion de " + nomUtilisateur + " impossible : " + reponse);
        }
        return reponse.get("accessToken").asString();
    }

    public Reponse get(String chemin, String jeton) {
        return call(HttpMethod.GET, chemin, jeton, null, MediaType.APPLICATION_JSON);
    }

    public Reponse post(String chemin, String jeton, Object corps) {
        return call(HttpMethod.POST, chemin, jeton, corps, MediaType.APPLICATION_JSON);
    }

    public Reponse put(String chemin, String jeton, Object corps) {
        return call(HttpMethod.PUT, chemin, jeton, corps, MediaType.APPLICATION_JSON);
    }

    /** Appel au serveur MCP (JSON-RPC) : le client MCP accepte JSON ou flux SSE. */
    public Reponse mcp(String jeton, String jsonRpc) {
        RestClient.RequestBodySpec requete = http.post().uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .body(jsonRpc);
        if (jeton != null) {
            requete.header("Authorization", "Bearer " + jeton);
        }
        return requete.exchange((req, rep) -> new Reponse(rep.getStatusCode().value(), parse(rep.bodyTo(String.class))));
    }

    private Reponse call(HttpMethod methode, String chemin, String jeton, Object corps, MediaType type) {
        RestClient.RequestBodySpec requete = http.method(methode).uri(chemin).accept(MediaType.APPLICATION_JSON);
        if (jeton != null) {
            requete.header("Authorization", "Bearer " + jeton);
        }
        if (corps != null) {
            requete.contentType(type).body(corps);
        }
        return requete.exchange((req, rep) -> new Reponse(rep.getStatusCode().value(), parse(rep.bodyTo(String.class))));
    }

    private static JsonNode parse(String texte) {
        if (texte == null || texte.isBlank()) {
            return JSON.missingNode();
        }
        try {
            return JSON.readTree(texte);
        } catch (RuntimeException e) {
            return JSON.getNodeFactory().textNode(texte);
        }
    }
}
