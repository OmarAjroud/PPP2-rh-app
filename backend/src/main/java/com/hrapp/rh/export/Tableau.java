package com.hrapp.rh.export;

import java.util.List;

/**
 * Contenu d'un export, indépendant du format : un titre, les filtres appliqués (en clair, ou null),
 * les colonnes et les lignes. Une valeur peut être null (cellule vide).
 * Valeurs attendues : String, Number (Long, BigDecimal...), LocalDate, LocalDateTime.
 */
public record Tableau(String titre, String filtres, List<Colonne> colonnes, List<List<Object>> lignes) {
}
