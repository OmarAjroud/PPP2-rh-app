package com.hrapp.rh.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Les requêtes d'agrégation du tableau de bord et l'audit automatique, exécutés par MySQL (pas de simulation). */
class TableauDeBordEtAuditIT extends IntegrationTest {

    @Test
    @DisplayName("Tableau de bord RH : effectifs et masse salariale calculés par la base sur les données de départ")
    void dashboard_shouldAggregateSeedData() {
        Api.Reponse tableau = api.get("/api/tableau-de-bord?annee=2022", api.login("rh"));

        assertThat(tableau.statut()).isEqualTo(200);
        assertThat(tableau.get("indicateurs").get("nombreEmployes").asInt()).isEqualTo(4);
        Map<String, Integer> effectifs = new HashMap<>();
        tableau.get("effectifParDepartement").forEach(d ->
                effectifs.put(d.get("departementNom").asString(), d.get("nombreEmployes").asInt()));
        assertThat(effectifs).containsEntry("Informatique", 3).containsEntry("Ressources Humaines", 1)
                .containsEntry("Finance", 0);

        // embauches 2022 : Amine en janvier, Sara en mars ; les 12 mois sont présents
        JsonNode mois = tableau.get("embauchesParMois");
        assertThat(mois.size()).isEqualTo(12);
        assertThat(mois.get(0).get("nombre").asInt()).isEqualTo(1);
        assertThat(mois.get(2).get("nombre").asInt()).isEqualTo(1);

        JsonNode informatique = null;
        for (JsonNode d : tableau.get("masseSalariale")) {
            if (d.get("departementNom").asString().equals("Informatique")) {
                informatique = d;
            }
        }
        assertThat(informatique).isNotNull();
        assertThat(informatique.get("total").asDouble()).isEqualTo(11700.0);          // 5000 + 4200 + 2500
        assertThat(informatique.get("moyenne").asDouble()).isEqualTo(3900.0);
    }

    @Test
    @DisplayName("Tableau de bord : manager limité à son département sans masse salariale, autre département 403, employé 403")
    void dashboard_shouldBeScopedByRole() {
        String karim = api.login("manager");

        Api.Reponse tableau = api.get("/api/tableau-de-bord", karim);
        assertThat(tableau.get("departementId").asLong()).isEqualTo(1);
        assertThat(tableau.get("indicateurs").get("nombreEmployes").asInt()).isEqualTo(3);
        assertThat(tableau.get("masseSalariale").isNull()).isTrue();

        assertThat(api.get("/api/tableau-de-bord?departementId=2", karim).statut()).isEqualTo(403);
        assertThat(api.get("/api/tableau-de-bord", api.login("employe")).statut()).isEqualTo(403);
        assertThat(api.get("/api/tableau-de-bord?departementId=99", api.login("admin")).statut()).isEqualTo(404);
    }

    @Test
    @DisplayName("Audit : une modification d'employé enregistre l'auteur et seulement les valeurs changées")
    void employeeUpdate_shouldBeAuditedWithOldAndNewValues() {
        String rh = api.login("rh");
        Map<String, Object> lina = new HashMap<>(Map.of("nom", "Gharbi", "prenom", "Lina", "email", "employe@rh.com",
                "dateEmbauche", "2023-09-01", "salaire", 2600, "departementId", 1, "roleId", 4, "managerId", 3));

        assertThat(api.put("/api/employes/4", rh, lina).statut()).isEqualTo(200);

        Api.Reponse audit = api.get("/api/admin/audit?action=MODIFICATION&entite=Employe&entiteId=4", api.login("admin"));
        JsonNode ligne = audit.get("contenu").get(0);
        assertThat(ligne.get("nomUtilisateur").asString()).isEqualTo("rh");
        assertThat(ligne.get("anciennesValeurs").get("salaire").asDouble()).isEqualTo(2500.0);
        assertThat(ligne.get("nouvellesValeurs").get("salaire").asDouble()).isEqualTo(2600.0);
        assertThat(ligne.get("nouvellesValeurs").has("nom")).isFalse();              // inchangé : non enregistré
    }

    @Test
    @DisplayName("Employés : un RH ne peut pas promouvoir quelqu'un ADMIN (403), et rien n'est modifié")
    void hr_shouldNotGrantAdminRole() {
        Map<String, Object> lina = Map.of("nom", "Gharbi", "prenom", "Lina", "email", "employe@rh.com",
                "dateEmbauche", "2023-09-01", "salaire", 2500, "departementId", 1, "roleId", 1, "managerId", 3);

        assertThat(api.put("/api/employes/4", api.login("rh"), lina).statut()).isEqualTo(403);
        assertThat(api.get("/api/employes/4", api.login("admin")).get("roleNom").asString()).isEqualTo("EMPLOYE");
    }
}
