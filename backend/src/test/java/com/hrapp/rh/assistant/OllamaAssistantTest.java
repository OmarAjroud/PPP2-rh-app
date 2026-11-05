package com.hrapp.rh.assistant;

import com.hrapp.rh.common.exception.ServiceIndisponibleException;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Moteur Ollama, contre un faux serveur HTTP qui répond au format réel de POST /api/chat. */
@ExtendWith(MockitoExtension.class)
class OllamaAssistantTest {

    private static final String URL = "http://ollama.test:11434";

    @Mock private OutilsRhService outils;

    private final UtilisateurConnecte karim = new UtilisateurConnecte(3L, 3L, 1L, "manager", "hash", Roles.MANAGER, true);
    private MockRestServiceServer serveur;
    private OllamaAssistant ollama;

    @BeforeEach
    void setUp() {
        given(outils.definitions()).willReturn(TestOutils.definitions());
        RestClient.Builder builder = RestClient.builder();
        serveur = MockRestServiceServer.bindTo(builder).build();
        ollama = new OllamaAssistant(builder, outils, new AssistantProperties(AssistantProperties.OLLAMA, 3,
                new AssistantProperties.Ollama(URL, "qwen3:4b-instruct", null, null, null), null));
    }

    @Test
    @DisplayName("Boucle : appel d'outil (arguments en objet), résultat renvoyé en message « tool », puis réponse")
    void repondre_shouldRunToolThenAnswer() {
        serveur.expect(requestTo(URL + "/api/chat")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("qwen3:4b-instruct"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.tools.length()").value(7))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("Consignes"))
                .andRespond(withSuccess("""
                        {"message":{"role":"assistant","content":"","tool_calls":[{"id":"call_1","function":{"index":0,
                         "name":"absences_periode","arguments":{"du":"2026-11-09","au":"2026-11-13","departementId":1}}}]},
                         "done":true}""", MediaType.APPLICATION_JSON));
        serveur.expect(requestTo(URL + "/api/chat"))
                .andExpect(jsonPath("$.messages.length()").value(4))           // système, question, appel, résultat
                .andExpect(jsonPath("$.messages[3].role").value("tool"))
                .andExpect(jsonPath("$.messages[3].tool_name").value("absences_periode"))
                .andExpect(jsonPath("$.messages[3].content").value("{\"totalElements\":1}"))
                .andRespond(withSuccess("""
                        {"message":{"role":"assistant","content":"Lina Gharbi est absente."},"done":true}""",
                        MediaType.APPLICATION_JSON));
        given(outils.execute(eq(karim), eq("absences_periode"), anyMap(), eq(OutilsRhService.Source.CHAT)))
                .willReturn(new ResultatOutil(false, "{\"totalElements\":1}"));

        ChatResponse reponse = ollama.repondre(karim, "Consignes", question("Qui est absent ?"));

        assertThat(reponse).isEqualTo(new ChatResponse("Lina Gharbi est absente.", List.of("absences_periode")));
        verify(outils).execute(karim, "absences_periode",
                Map.of("du", "2026-11-09", "au", "2026-11-13", "departementId", 1), OutilsRhService.Source.CHAT);
        serveur.verify();
    }

    @Test
    @DisplayName("Erreur d'outil : transmise au modèle préfixée « ERREUR », la conversation continue")
    void repondre_shouldPassToolErrorsToTheModel() {
        serveur.expect(requestTo(URL + "/api/chat")).andRespond(withSuccess("""
                {"message":{"role":"assistant","content":"","tool_calls":[{"function":{"name":"consulter_employe",
                 "arguments":{"employeId":3}}}]}}""", MediaType.APPLICATION_JSON));
        serveur.expect(requestTo(URL + "/api/chat"))
                .andExpect(jsonPath("$.messages[3].content").value("ERREUR : Accès refusé à cet employé"))
                .andRespond(withSuccess("{\"message\":{\"role\":\"assistant\",\"content\":\"Accès refusé.\"}}",
                        MediaType.APPLICATION_JSON));
        given(outils.execute(any(), anyString(), anyMap(), any()))
                .willReturn(new ResultatOutil(true, "Accès refusé à cet employé"));

        assertThat(ollama.repondre(karim, "Consignes", question("Salaire de Karim ?")).reponse()).isEqualTo("Accès refusé.");
        serveur.verify();
    }

    @Test
    @DisplayName("Raisonnement éventuel (« ... </think> ») : retiré de la réponse")
    void repondre_shouldStripReasoning() {
        serveur.expect(requestTo(URL + "/api/chat")).andRespond(withSuccess("""
                {"message":{"role":"assistant","content":"Je réfléchis...\\n</think>\\n\\nIl vous reste 22 jours."}}""",
                MediaType.APPLICATION_JSON));

        assertThat(ollama.repondre(karim, "Consignes", question("Mon solde ?")).reponse()).isEqualTo("Il vous reste 22 jours.");
    }

    @Test
    @DisplayName("Garde-fou : la boucle s'arrête après le nombre maximal d'allers-retours")
    void repondre_shouldStop_afterMaxIterations() {
        String appel = """
                {"message":{"role":"assistant","content":"","tool_calls":[{"function":{"name":"lister_departements","arguments":{}}}]}}""";
        for (int i = 0; i < 3; i++) {
            serveur.expect(requestTo(URL + "/api/chat")).andRespond(withSuccess(appel, MediaType.APPLICATION_JSON));
        }
        given(outils.execute(any(), anyString(), anyMap(), any())).willReturn(new ResultatOutil(false, "[]"));

        assertThat(ollama.repondre(karim, "Consignes", question("...")).reponse()).contains("trop d'étapes");
        serveur.verify();
    }

    @Test
    @DisplayName("Modèle non installé (404) : 503 avec la commande « ollama pull » à lancer")
    void repondre_shouldExplainHowToInstallMissingModel() {
        serveur.expect(requestTo(URL + "/api/chat")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .body("{\"error\":\"model 'qwen3:4b-instruct' not found\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ollama.repondre(karim, "Consignes", question("Bonjour")))
                .isInstanceOf(ServiceIndisponibleException.class)
                .hasMessageContaining("ollama pull qwen3:4b-instruct");
        verify(outils, never()).execute(any(), anyString(), anyMap(), any());
    }

    @Test
    @DisplayName("Statut : disponible si le modèle est installé, sinon la commande à lancer")
    void statut_shouldReportInstalledModel() {
        serveur.expect(requestTo(URL + "/api/tags")).andRespond(withSuccess(
                "{\"models\":[{\"name\":\"qwen3:4b\"},{\"name\":\"qwen3:4b-instruct\"}]}", MediaType.APPLICATION_JSON));
        serveur.expect(requestTo(URL + "/api/tags")).andRespond(withSuccess(
                "{\"models\":[{\"name\":\"qwen3:4b\"}]}", MediaType.APPLICATION_JSON));

        assertThat(ollama.statut()).containsEntry("disponible", true).containsEntry("modele", "qwen3:4b-instruct");
        assertThat(ollama.statut()).containsEntry("disponible", false)
                .containsEntry("message", "Modèle non installé : lancez « ollama pull qwen3:4b-instruct »");
    }

    @Test
    @DisplayName("Préchargement : demande à Ollama de garder le modèle en mémoire ; une erreur ne bloque pas le démarrage")
    void preload_shouldAskOllamaToKeepModelLoaded() {
        serveur.expect(requestTo(URL + "/api/generate"))
                .andExpect(content().json("{\"model\":\"qwen3:4b-instruct\",\"keep_alive\":\"30m\"}"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        ollama.preload();                                                 // ne lève rien

        serveur.verify();
    }

    private static List<ChatRequest.Message> question(String texte) {
        return List.of(new ChatRequest.Message("utilisateur", texte));
    }
}
