package com.hrapp.rh.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.hrapp.rh.common.exception.ServiceIndisponibleException;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Moteur Claude (optionnel) : la boucle outils sur des réponses au format réel de l'API, désérialisées par le SDK. */
@ExtendWith(MockitoExtension.class)
class ClaudeAssistantTest {

    private static final String CONSIGNES = "Consignes de test";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private AnthropicClient client;
    @Mock private ObjectProvider<AnthropicClient> clientProvider;
    @Mock private OutilsRhService outils;

    private final UtilisateurConnecte karim = new UtilisateurConnecte(3L, 3L, 1L, "manager", "hash", Roles.MANAGER, true);
    private ClaudeAssistant claude;

    @BeforeEach
    void setUp() {
        given(outils.definitions()).willReturn(TestOutils.definitions());
        claude = new ClaudeAssistant(clientProvider, outils,
                new AssistantProperties(AssistantProperties.CLAUDE, 3, null,
                        new AssistantProperties.Claude("sk-test", null, null)));
    }

    @Test
    @DisplayName("Boucle : le modèle appelle un outil, reçoit le résultat, puis répond")
    void repondre_shouldRunToolThenAnswer() {
        given(clientProvider.getIfAvailable()).willReturn(client);
        given(client.beta().messages().create(any(MessageCreateParams.class))).willReturn(
                toolUse("absences_periode", "{\"du\":\"2026-11-09\",\"au\":\"2026-11-13\",\"departementId\":1}"),
                text("Lina Gharbi sera absente lundi.", "end_turn"));
        given(outils.execute(eq(karim), eq("absences_periode"), anyMap(), eq(OutilsRhService.Source.CHAT)))
                .willReturn(new ResultatOutil(false, "{\"totalElements\":1}"));

        ChatResponse reponse = claude.repondre(karim, CONSIGNES, question("Qui est absent la semaine prochaine ?"));

        assertThat(reponse.reponse()).isEqualTo("Lina Gharbi sera absente lundi.");
        assertThat(reponse.outilsUtilises()).containsExactly("absences_periode");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> args = ArgumentCaptor.forClass(Map.class);
        verify(outils).execute(eq(karim), eq("absences_periode"), args.capture(), eq(OutilsRhService.Source.CHAT));
        assertThat(args.getValue()).containsEntry("du", "2026-11-09").containsEntry("departementId", 1);

        // 2e appel : question + réponse du modèle (outil) + résultat de l'outil
        ArgumentCaptor<MessageCreateParams> params = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(client.beta().messages(), times(2)).create(params.capture());
        assertThat(params.getAllValues().get(1).messages()).hasSize(3);
    }

    @Test
    @DisplayName("Requête : modèle par défaut, consignes reçues, 7 outils, repli en cas de refus")
    void repondre_shouldSendModelToolsConsignesAndFallbacks() {
        given(clientProvider.getIfAvailable()).willReturn(client);
        given(client.beta().messages().create(any(MessageCreateParams.class))).willReturn(text("Bonjour !", "end_turn"));

        claude.repondre(karim, CONSIGNES, question("Bonjour"));

        ArgumentCaptor<MessageCreateParams> params = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(client.beta().messages()).create(params.capture());
        MessageCreateParams envoye = params.getValue();
        assertThat(envoye.model().asString()).isEqualTo("claude-opus-5-5");
        assertThat(envoye.tools()).get().asList().hasSize(7);
        assertThat(envoye.fallbacks()).isPresent();
        assertThat(envoye.betas()).get().asList().extracting(Object::toString).contains(ClaudeAssistant.BETA_FALLBACK);
        assertThat(envoye.system().orElseThrow().asString()).isEqualTo(CONSIGNES);
    }

    @Test
    @DisplayName("Refus du modèle : message neutre, aucun outil exécuté")
    void repondre_shouldReturnNeutralMessage_onRefusal() {
        given(clientProvider.getIfAvailable()).willReturn(client);
        given(client.beta().messages().create(any(MessageCreateParams.class))).willReturn(text("", "refusal"));

        ChatResponse reponse = claude.repondre(karim, CONSIGNES, question("..."));

        assertThat(reponse.reponse()).isEqualTo("Je ne peux pas répondre à cette demande.");
        verify(outils, never()).execute(any(), anyString(), anyMap(), any());
    }

    @Test
    @DisplayName("Garde-fou : la boucle s'arrête après le nombre maximal d'allers-retours")
    void repondre_shouldStop_afterMaxIterations() {
        given(clientProvider.getIfAvailable()).willReturn(client);
        given(client.beta().messages().create(any(MessageCreateParams.class)))
                .willReturn(toolUse("lister_departements", "{}"));
        given(outils.execute(any(), anyString(), anyMap(), any())).willReturn(new ResultatOutil(false, "[]"));

        ChatResponse reponse = claude.repondre(karim, CONSIGNES, question("..."));

        assertThat(reponse.reponse()).contains("trop d'étapes");
        verify(client.beta().messages(), times(3)).create(any(MessageCreateParams.class));
    }

    @Test
    @DisplayName("Réponse tronquée (max_tokens) : le texte est renvoyé avec un avertissement")
    void repondre_shouldFlagTruncatedAnswer() {
        given(clientProvider.getIfAvailable()).willReturn(client);
        given(client.beta().messages().create(any(MessageCreateParams.class))).willReturn(text("Début", "max_tokens"));

        assertThat(claude.repondre(karim, CONSIGNES, question("...")).reponse()).startsWith("Début").contains("tronquée");
    }

    @Test
    @DisplayName("Sans clé d'API : 503, aucun appel ; statut « indisponible »")
    void repondre_shouldThrowUnavailable_whenNotConfigured() {
        given(clientProvider.getIfAvailable()).willReturn(null);

        assertThatThrownBy(() -> claude.repondre(karim, CONSIGNES, question("Bonjour")))
                .isInstanceOf(ServiceIndisponibleException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
        assertThat(claude.statut()).containsEntry("disponible", false).containsEntry("moteur", "claude");
    }

    @Test
    @DisplayName("API injoignable : 503 avec un message lisible")
    void repondre_shouldThrowUnavailable_whenApiFails() {
        given(clientProvider.getIfAvailable()).willReturn(client);
        given(client.beta().messages().create(any(MessageCreateParams.class)))
                .willThrow(new AnthropicIoException("connexion refusée", null));

        assertThatThrownBy(() -> claude.repondre(karim, CONSIGNES, question("Bonjour")))
                .isInstanceOf(ServiceIndisponibleException.class)
                .hasMessageContaining("momentanément indisponible");
    }

    @Test
    @DisplayName("Définitions Claude : schéma JSON avec paramètres et champs obligatoires")
    void claudeTools_shouldExposeSchemaWithRequiredFields() {
        var absences = claude.claudeTools().stream().filter(t -> t.name().equals("absences_periode")).findFirst()
                .orElseThrow();

        assertThat(absences.inputSchema().required()).get().asList().containsExactlyInAnyOrder("du", "au");
        assertThat(absences.inputSchema().properties()).isPresent();
    }

    // ---------- réponses de l'API (format JSON réel, désérialisé par le SDK) ----------

    private static BetaMessage toolUse(String outil, String inputJson) {
        return parse("""
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[{"type":"tool_use","id":"toolu_1","name":"%s","input":%s}],
                 "stop_reason":"tool_use","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """.formatted(outil, inputJson));
    }

    private static BetaMessage text(String texte, String arret) {
        return parse("""
                {"id":"msg_2","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[{"type":"text","text":"%s"}],
                 "stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """.formatted(texte, arret));
    }

    private static BetaMessage parse(String json) {
        try {
            return ObjectMappers.jsonMapper().readValue(json, BetaMessage.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<ChatRequest.Message> question(String texte) {
        return List.of(new ChatRequest.Message("utilisateur", texte));
    }
}
