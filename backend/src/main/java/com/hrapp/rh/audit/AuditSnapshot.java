package com.hrapp.rh.audit;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Calcule les différences entre deux états d'une entité. */
public final class AuditSnapshot {

    private AuditSnapshot() {}

    /** Valeurs modifiées : anciennes et nouvelles, avec les mêmes clés. */
    public record Diff(Map<String, Object> anciennes, Map<String, Object> nouvelles) {
        public boolean isEmpty() {
            return nouvelles.isEmpty();
        }
    }

    public static Diff diff(Map<String, Object> avant, Map<String, Object> apres) {
        Map<String, Object> anciennes = new LinkedHashMap<>();
        Map<String, Object> nouvelles = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entree : apres.entrySet()) {
            Object ancienne = avant.get(entree.getKey());
            if (!sameValue(ancienne, entree.getValue())) {
                anciennes.put(entree.getKey(), ancienne);
                nouvelles.put(entree.getKey(), entree.getValue());
            }
        }
        return new Diff(anciennes, nouvelles);
    }

    /** 2500.00 et 2500 sont la même valeur (les BigDecimal se comparent avec compareTo, pas equals). */
    static boolean sameValue(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }
}