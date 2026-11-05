package com.hrapp.rh.assistant;

import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    @Mock private ConsignesAssistant consignes;

    private final UtilisateurConnecte karim = new UtilisateurConnecte(3L, 3L, 1L, "manager", "hash", Roles.MANAGER, true);

    @Test
    @DisplayName("Moteur : celui de app.assistant.provider reçoit la question et les consignes")
    void chat_shouldDelegateToConfiguredEngine() {
        ModeleAssistant ollama = engine("ollama");
        ModeleAssistant claude = engine("claude");
        AssistantService service = new AssistantService(List.of(claude, ollama), consignes, properties("ollama"));
        List<ChatRequest.Message> messages = List.of(new ChatRequest.Message("utilisateur", "Bonjour"));
        given(consignes.build(karim)).willReturn("Consignes");
        given(ollama.repondre(karim, "Consignes", messages)).willReturn(new ChatResponse("Salut", List.of()));

        assertThat(service.chat(karim, new ChatRequest(messages)).reponse()).isEqualTo("Salut");
        verify(claude, never()).repondre(any(), any(), any());
    }

    @Test
    @DisplayName("Moteur inconnu : l'application refuse de démarrer avec un message clair")
    void constructor_shouldRejectUnknownEngine() {
        assertThatThrownBy(() -> new AssistantService(List.of(engine("ollama")), consignes, properties("gpt")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gpt").hasMessageContaining("ollama");
    }

    @Test
    @DisplayName("Conversation : doit commencer et se terminer par l'utilisateur (sinon 400, aucun appel au modèle)")
    void chat_shouldReject_whenLastMessageIsFromAssistant() {
        ModeleAssistant ollama = engine("ollama");
        AssistantService service = new AssistantService(List.of(ollama), consignes, properties("ollama"));
        ChatRequest requete = new ChatRequest(List.of(
                new ChatRequest.Message("utilisateur", "Bonjour"),
                new ChatRequest.Message("assistant", "Bonjour !")));

        assertThatThrownBy(() -> service.chat(karim, requete)).isInstanceOf(RequeteInvalideException.class);
        verify(ollama, never()).repondre(any(), any(), any());
    }

    @Test
    @DisplayName("Statut : celui du moteur configuré")
    void statut_shouldComeFromConfiguredEngine() {
        ModeleAssistant ollama = engine("ollama");
        given(ollama.statut()).willReturn(Map.of("moteur", "ollama", "disponible", true));

        assertThat(new AssistantService(List.of(ollama), consignes, properties("ollama")).statut())
                .containsEntry("disponible", true);
    }

    @Test
    @DisplayName("Configuration : Ollama et qwen3:4b-instruct par défaut")
    void properties_shouldDefaultToLocalOllama() {
        AssistantProperties defaut = new AssistantProperties(null, null, null, null);

        assertThat(defaut.provider()).isEqualTo("ollama");
        assertThat(defaut.ollama().model()).isEqualTo("qwen3:4b-instruct");
        assertThat(defaut.ollama().url()).isEqualTo("http://localhost:11434");
        assertThat(defaut.claude().apiKey()).isEmpty();
    }

    private static ModeleAssistant engine(String nom) {
        ModeleAssistant moteur = mock(ModeleAssistant.class);
        given(moteur.nom()).willReturn(nom);
        return moteur;
    }

    private static AssistantProperties properties(String provider) {
        return new AssistantProperties(provider, null, null, null);
    }
}
