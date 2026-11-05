package com.hrapp.rh.assistant;

import com.hrapp.rh.securite.UtilisateurConnecte;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Assistant RH : tout utilisateur connecté, chacun avec ses propres droits. */
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService service;
    private final OutilsRhService outils;

    /**
     * Exemple : {"messages": [{"role": "utilisateur", "contenu": "Qui est en congé la semaine prochaine en informatique ?"}]}
     * Le front-end renvoie toute la conversation à chaque question (l'API ne garde pas d'historique).
     */
    @PostMapping("/chat")
    public ChatResponse chat(@AuthenticationPrincipal UtilisateurConnecte utilisateur,
                             @Valid @RequestBody ChatRequest requete) {
        return service.chat(utilisateur, requete);
    }

    /**
     * L'assistant est-il disponible, avec quel moteur et quel modèle ? (pour afficher ou masquer le chat)
     * Exemple : {"moteur": "ollama", "modele": "qwen3:4b-instruct", "disponible": true, "outils": [...]}
     */
    @GetMapping("/statut")
    public Map<String, Object> status() {
        Map<String, Object> statut = new LinkedHashMap<>(service.statut());
        statut.put("outils", outils.definitions().stream().map(OutilRh::nom).toList());
        return statut;
    }
}
