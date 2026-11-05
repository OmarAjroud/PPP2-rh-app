package com.hrapp.rh.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Serveur MCP (servlet distinct, vraie requête HTTP), chat sans modèle disponible, flux SSE des notifications. */
class AssistantEtNotificationsIT extends IntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("MCP : sans jeton 401 ; avec jeton, les 7 outils sont listés")
    void mcp_shouldRequireTokenAndListTools() {
        String liste = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

        assertThat(api.mcp(null, liste).statut()).isEqualTo(401);
        Api.Reponse outils = api.mcp(api.login("rh"), liste);
        assertThat(outils.statut()).isEqualTo(200);
        assertThat(outils.get("result").get("tools").size()).isEqualTo(7);
    }

    @Test
    @DisplayName("MCP : les outils appliquent les droits de l'utilisateur du jeton (salaire masqué pour le manager) et sont audités")
    void mcpTools_shouldApplyTokenUserRights_andBeAudited() {
        String appel = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"consulter_employe\",\"arguments\":{\"employeId\":4}}}";

        JsonNode pourRh = toolResult(api.mcp(api.login("rh"), appel));
        JsonNode pourManager = toolResult(api.mcp(api.login("manager"), appel));
        Api.Reponse pourEmployeAutre = api.mcp(api.login("employe"), appel.replace("\"employeId\":4", "\"employeId\":3"));

        assertThat(pourRh.get("salaire").asDouble()).isEqualTo(2500.0);
        assertThat(pourManager.get("salaire").isNull()).isTrue();
        assertThat(pourEmployeAutre.get("result").get("isError").asBoolean()).isTrue();

        Api.Reponse audit = api.get("/api/admin/audit?action=APPEL_ASSISTANT", api.login("admin"));
        assertThat(audit.get("totalElements").asLong()).isEqualTo(3);
        assertThat(audit.get("contenu").get(0).get("details").asString()).contains("\"source\":\"MCP\"");
    }

    @Test
    @DisplayName("Chat : modèle local injoignable → 503 avec une explication ; statut « indisponible » ; conversation invalide 400")
    void chat_withoutModel_shouldFailClearly() {
        String rh = api.login("rh");

        Api.Reponse chat = api.post("/api/assistant/chat", rh,
                Map.of("messages", java.util.List.of(Map.of("role", "utilisateur", "contenu", "Bonjour"))));
        assertThat(chat.statut()).isEqualTo(503);
        assertThat(chat.get("message").asString()).contains("Ollama");

        Api.Reponse statut = api.get("/api/assistant/statut", rh);
        assertThat(statut.get("moteur").asString()).isEqualTo("ollama");
        assertThat(statut.get("disponible").asBoolean()).isFalse();

        assertThat(api.post("/api/assistant/chat", rh, Map.of("messages", java.util.List.of())).statut()).isEqualTo(400);
    }

    @Test
    @DisplayName("SSE : ticket à usage unique ; le flux s'ouvre et envoie d'abord le compteur de la cloche")
    void notificationStream_shouldOpenWithTicket_andSendUnreadCountFirst() throws Exception {
        String lina = api.login("employe");
        String ticket = api.post("/api/notifications/flux/ticket", lina, null).get("ticket").asString();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<Stream<String>> flux = client.send(HttpRequest.newBuilder(
                        URI.create(baseUrl() + "/api/notifications/flux?ticket=" + ticket))
                .header("Accept", "text/event-stream").timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofLines());

        assertThat(flux.statusCode()).isEqualTo(200);
        try (Stream<String> lignes = flux.body()) {
            Iterator<String> it = lignes.iterator();
            assertThat(it.next()).isEqualTo("event:non-lues");
            assertThat(it.next()).isEqualTo("data:{\"nombre\":0}");
        }

        // le même ticket ne sert qu'une fois
        HttpResponse<Void> rejoue = client.send(HttpRequest.newBuilder(
                        URI.create(baseUrl() + "/api/notifications/flux?ticket=" + ticket)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(rejoue.statusCode()).isEqualTo(401);
    }

    // ---------- méthodes utilitaires ----------

    /** Le résultat d'un outil MCP est du JSON dans le premier contenu texte. */
    private static JsonNode toolResult(Api.Reponse reponse) {
        assertThat(reponse.get("result").get("isError").asBoolean()).isFalse();
        return JSON.readTree(reponse.get("result").get("content").get(0).get("text").asString());
    }
}
