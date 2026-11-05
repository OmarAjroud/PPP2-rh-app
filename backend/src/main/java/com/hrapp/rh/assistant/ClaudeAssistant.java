package com.hrapp.rh.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.hrapp.rh.common.exception.ServiceIndisponibleException;
import com.hrapp.rh.securite.UtilisateurConnecte;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Moteur optionnel : l'API Claude (clé ANTHROPIC_API_KEY requise, usage facturé).
 * Boucle « modèle → outils → modèle » avec le SDK officiel.
 */
@Slf4j
@Component
public class ClaudeAssistant implements ModeleAssistant {

    static final String BETA_FALLBACK = "server-side-fallback-2026-07-01";
    private static final long MAX_TOKENS = 16_000;

    private final ObjectProvider<AnthropicClient> clientProvider;
    private final OutilsRhService outils;
    private final AssistantProperties.Claude config;
    private final int maxIterations;
    private final List<BetaTool> definitionsClaude;

    public ClaudeAssistant(ObjectProvider<AnthropicClient> clientProvider, OutilsRhService outils,
                           AssistantProperties properties) {
        this.clientProvider = clientProvider;
        this.outils = outils;
        this.config = properties.claude();
        this.maxIterations = properties.maxIterations();
        this.definitionsClaude = outils.definitions().stream().map(ClaudeAssistant::toClaudeTool).toList();
    }

    @Override
    public String nom() {
        return AssistantProperties.CLAUDE;
    }

    @Override
    public ChatResponse repondre(UtilisateurConnecte utilisateur, String consignes, List<ChatRequest.Message> messages) {
        AnthropicClient client = clientProvider.getIfAvailable();
        if (client == null) {
            throw new ServiceIndisponibleException(
                    "Le moteur Claude n'est pas configuré (clé ANTHROPIC_API_KEY absente)");
        }

        MessageCreateParams.Builder conversation = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(MAX_TOKENS)
                .system(consignes)
                .outputConfig(BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.of(config.effort()))
                        .build())
                // si le modèle principal refuse, l'API relance la demande sur un modèle de repli adapté
                .addBeta(BETA_FALLBACK)
                .fallbacksDefault();
        definitionsClaude.forEach(conversation::addTool);
        for (ChatRequest.Message m : messages) {
            if (m.deLUtilisateur()) {
                conversation.addUserMessage(m.contenu());
            } else {
                conversation.addAssistantMessage(m.contenu());
            }
        }

        List<String> outilsUtilises = new ArrayList<>();
        for (int tour = 0; tour < maxIterations; tour++) {
            BetaMessage reponse = call(client, conversation.build());
            BetaStopReason arret = reponse.stopReason().orElse(BetaStopReason.END_TURN);

            if (BetaStopReason.REFUSAL.equals(arret)) {
                return new ChatResponse("Je ne peux pas répondre à cette demande.", outilsUtilises);
            }
            List<BetaToolUseBlock> appels = reponse.content().stream()
                    .flatMap(bloc -> bloc.toolUse().stream())
                    .toList();
            if (BetaStopReason.TOOL_USE.equals(arret) && !appels.isEmpty()) {
                conversation.addMessage(reponse);                      // blocs renvoyés tels quels (réflexion comprise)
                List<BetaContentBlockParam> resultats = new ArrayList<>();
                for (BetaToolUseBlock appel : appels) {
                    outilsUtilises.add(appel.name());
                    ResultatOutil resultat = outils.execute(utilisateur, appel.name(), arguments(appel),
                            OutilsRhService.Source.CHAT);
                    resultats.add(BetaContentBlockParam.ofToolResult(BetaToolResultBlockParam.builder()
                            .toolUseId(appel.id())
                            .content(resultat.contenu())
                            .isError(resultat.erreur())
                            .build()));
                }
                conversation.addUserMessageOfContentBlockParams(resultats);   // tous les résultats ensemble
                continue;
            }
            if (BetaStopReason.PAUSE_TURN.equals(arret)) {
                conversation.addMessage(reponse);
                continue;
            }
            String texte = text(reponse);
            if (BetaStopReason.MAX_TOKENS.equals(arret)) {
                texte += "\n\n(Réponse tronquée : posez une question plus précise.)";
            }
            return new ChatResponse(texte.isBlank() ? "Je n'ai pas de réponse à proposer." : texte, outilsUtilises);
        }
        return new ChatResponse("Cette question demande trop d'étapes : pouvez-vous la préciser ou la découper ?",
                outilsUtilises);
    }

    @Override
    public Map<String, Object> statut() {
        Map<String, Object> statut = new LinkedHashMap<>();
        statut.put("moteur", nom());
        statut.put("modele", config.model());
        boolean configure = clientProvider.getIfAvailable() != null;
        statut.put("disponible", configure);
        if (!configure) {
            statut.put("message", "Clé ANTHROPIC_API_KEY absente");
        }
        return statut;
    }

    // ---------- méthodes privées ----------

    private BetaMessage call(AnthropicClient client, MessageCreateParams params) {
        try {
            return client.beta().messages().create(params);
        } catch (RateLimitException e) {
            log.warn("Assistant RH : limite de débit de l'API Claude atteinte");
            throw new ServiceIndisponibleException("L'assistant est très sollicité : réessayez dans un instant");
        } catch (AnthropicServiceException e) {
            log.error("Assistant RH : erreur de l'API Claude (HTTP {})", e.statusCode(), e);
            throw new ServiceIndisponibleException("L'assistant est momentanément indisponible");
        } catch (AnthropicException e) {
            log.error("Assistant RH : appel à l'API Claude impossible", e);
            throw new ServiceIndisponibleException("L'assistant est momentanément indisponible");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> arguments(BetaToolUseBlock appel) {
        try {
            Map<String, Object> args = appel._input().convert(Map.class);
            return args == null ? Map.of() : args;
        } catch (RuntimeException e) {
            return Map.of();                                       // entrée illisible : l'outil signalera le paramètre manquant
        }
    }

    private static String text(BetaMessage reponse) {
        return reponse.content().stream()
                .flatMap(bloc -> bloc.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining("\n"))
                .trim();
    }

    private static BetaTool toClaudeTool(OutilRh outil) {
        BetaTool.InputSchema.Properties.Builder proprietes = BetaTool.InputSchema.Properties.builder();
        outil.proprietes().forEach((nom, schema) -> proprietes.putAdditionalProperty(nom, JsonValue.from(schema)));
        return BetaTool.builder()
                .name(outil.nom())
                .description(outil.description())
                .inputSchema(BetaTool.InputSchema.builder()
                        .properties(proprietes.build())
                        .required(outil.obligatoires())
                        .build())
                .build();
    }

    /** Pour les tests : les définitions envoyées à Claude. */
    List<BetaTool> claudeTools() {
        return definitionsClaude;
    }
}
