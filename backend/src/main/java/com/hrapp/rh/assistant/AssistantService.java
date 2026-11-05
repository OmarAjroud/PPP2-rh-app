package com.hrapp.rh.assistant;

import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.securite.UtilisateurConnecte;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Point d'entrée du chat : valide la conversation, prépare les consignes et confie la réponse
 * au moteur choisi par app.assistant.provider (Ollama par défaut, Claude en option).
 */
@Service
public class AssistantService {

    private final ModeleAssistant moteur;
    private final ConsignesAssistant consignes;

    public AssistantService(List<ModeleAssistant> moteurs, ConsignesAssistant consignes, AssistantProperties properties) {
        Map<String, ModeleAssistant> parNom = moteurs.stream()
                .collect(Collectors.toMap(ModeleAssistant::nom, Function.identity()));
        this.moteur = parNom.get(properties.provider());
        if (this.moteur == null) {
            throw new IllegalStateException("app.assistant.provider inconnu : « " + properties.provider()
                    + " » (valeurs possibles : " + parNom.keySet() + ")");
        }
        this.consignes = consignes;
    }

    public ChatResponse chat(UtilisateurConnecte utilisateur, ChatRequest requete) {
        List<ChatRequest.Message> messages = requete.messages();
        if (!messages.getFirst().deLUtilisateur() || !messages.getLast().deLUtilisateur()) {
            throw new RequeteInvalideException(
                    "La conversation doit commencer et se terminer par un message de l'utilisateur");
        }
        return moteur.repondre(utilisateur, consignes.build(utilisateur), messages);
    }

    public Map<String, Object> statut() {
        return moteur.statut();
    }
}
