package com.hrapp.rh.assistant;

import com.hrapp.rh.securite.UtilisateurConnecte;

import java.util.List;
import java.util.Map;

/**
 * Un moteur de chat (modèle de langage) capable d'appeler les outils RH.
 * Deux implémentations : Ollama (modèle local, par défaut) et Claude (API).
 */
public interface ModeleAssistant {

    /** Identifiant du moteur, tel qu'écrit dans app.assistant.provider. */
    String nom();

    /**
     * Répond à la dernière question de la conversation, en appelant les outils si besoin.
     *
     * @param consignes consignes système complètes (règles + contexte de l'utilisateur)
     * @param messages  conversation validée : commence et se termine par un message de l'utilisateur
     */
    ChatResponse repondre(UtilisateurConnecte utilisateur, String consignes, List<ChatRequest.Message> messages);

    /** État du moteur pour l'écran de chat (disponible ou non, modèle, explication). */
    Map<String, Object> statut();
}
