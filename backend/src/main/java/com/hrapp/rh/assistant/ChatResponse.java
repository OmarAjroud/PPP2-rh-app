package com.hrapp.rh.assistant;

import java.util.List;

/**
 * Réponse de l'assistant.
 *
 * @param outilsUtilises outils appelés pour répondre, dans l'ordre (utile pour l'affichage et le débogage)
 */
public record ChatResponse(String reponse, List<String> outilsUtilises) {}
