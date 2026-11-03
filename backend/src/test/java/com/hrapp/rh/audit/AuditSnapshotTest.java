package com.hrapp.rh.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class AuditSnapshotTest {

    @Test
    @DisplayName("Différences : ne garde que les valeurs modifiées")
    void diff_shouldKeepOnlyChangedValues() {
        Map<String, Object> avant = map("nom", "Marketing", "description", "Publicité");
        Map<String, Object> apres = map("nom", "Marketing & Com", "description", "Publicité");

        AuditSnapshot.Diff diff = AuditSnapshot.diff(avant, apres);

        assertThat(diff.anciennes()).containsExactly(entry("nom", "Marketing"));
        assertThat(diff.nouvelles()).containsExactly(entry("nom", "Marketing & Com"));
    }

    @Test
    @DisplayName("Différences : aucune modification, donc rien à enregistrer")
    void diff_shouldBeEmpty_whenNothingChanged() {
        Map<String, Object> avant = map("nom", "Finance", "departementId", 3L);
        Map<String, Object> apres = map("nom", "Finance", "departementId", 3L);

        assertThat(AuditSnapshot.diff(avant, apres).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Différences : 2500.00 et 2500 sont la même valeur (BigDecimal)")
    void diff_shouldIgnoreScaleDifference_whenBigDecimalsAreEqual() {
        Map<String, Object> avant = map("salaire", new BigDecimal("2500.00"));
        Map<String, Object> apres = map("salaire", new BigDecimal("2500"));

        assertThat(AuditSnapshot.diff(avant, apres).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Différences : un salaire qui change est bien détecté")
    void diff_shouldDetectChange_whenBigDecimalsDiffer() {
        Map<String, Object> avant = map("salaire", new BigDecimal("2500.00"));
        Map<String, Object> apres = map("salaire", new BigDecimal("2600"));

        AuditSnapshot.Diff diff = AuditSnapshot.diff(avant, apres);

        assertThat(diff.isEmpty()).isFalse();
        assertThat(diff.anciennes().get("salaire")).isEqualTo(new BigDecimal("2500.00"));
        assertThat(diff.nouvelles().get("salaire")).isEqualTo(new BigDecimal("2600"));
    }

    @Test
    @DisplayName("Différences : passer de « aucun manager » à un manager est enregistré")
    void diff_shouldDetectChange_whenValueGoesFromNullToSomething() {
        Map<String, Object> avant = map("managerId", null);
        Map<String, Object> apres = map("managerId", 3L);

        AuditSnapshot.Diff diff = AuditSnapshot.diff(avant, apres);

        assertThat(diff.anciennes()).containsEntry("managerId", null);
        assertThat(diff.nouvelles()).containsEntry("managerId", 3L);
    }

    /** Fabrique une Map à partir de paires clé, valeur (accepte les valeurs null). */
    private Map<String, Object> map(Object... paires) {
        Map<String, Object> resultat = new LinkedHashMap<>();
        for (int i = 0; i < paires.length; i += 2) {
            resultat.put((String) paires[i], paires[i + 1]);
        }
        return resultat;
    }
}