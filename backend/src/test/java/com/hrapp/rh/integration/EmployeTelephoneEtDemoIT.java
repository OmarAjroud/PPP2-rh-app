package com.hrapp.rh.integration;

import com.hrapp.rh.employe.DemoEmployesInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Téléphone de l'employé, salaire en dinars (3 décimales) et employés de démonstration. */
class EmployeTelephoneEtDemoIT extends IntegrationTest {

    @Autowired
    private DemoEmployesInitializer demo;

    private static Map<String, Object> employe(String email, String telephone, Object salaire) {
        Map<String, Object> m = new HashMap<>();
        m.put("nom", "Haddad");
        m.put("prenom", "Sami");
        m.put("email", email);
        m.put("telephone", telephone);
        m.put("dateEmbauche", "2026-01-05");
        m.put("salaire", salaire);
        m.put("departementId", 1);
        m.put("roleId", 4);
        return m;
    }

    @Test
    @DisplayName("Le téléphone est obligatoire à la création, vérifié, enregistré proprement et renvoyé sur la fiche")
    void phone_shouldBeRequiredValidatedAndStored() {
        String rh = api.login("rh");

        Api.Reponse sans = api.post("/api/employes", rh, employe("sans@rh.com", null, 2000));
        assertThat(sans.statut()).isEqualTo(400);
        assertThat(sans.get("erreursChamps").get("telephone").asString()).contains("obligatoire");

        Api.Reponse invalide = api.post("/api/employes", rh, employe("invalide@rh.com", "appelez-moi", 2000));
        assertThat(invalide.statut()).isEqualTo(400);
        assertThat(invalide.get("erreursChamps").has("telephone")).isTrue();

        // too few digits, even if the shape looks right
        assertThat(api.post("/api/employes", rh, employe("court@rh.com", "1------2", 2000)).statut()).isEqualTo(400);

        // a non-breaking space pasted from a document is accepted and cleaned
        Api.Reponse cree = api.post("/api/employes", rh, employe("sami.haddad@rh.com", "  +216 22  345 678 ", 2345.678));
        assertThat(cree.statut()).isEqualTo(201);
        assertThat(cree.get("telephone").asString()).isEqualTo("+216 22 345 678");
        // dinars: 3 decimals (millimes) are kept
        assertThat(cree.get("salaire").decimalValue()).isEqualByComparingTo("2345.678");
    }

    @Test
    @DisplayName("Les employés de départ ont un numéro de téléphone")
    void seedEmployees_shouldHaveAPhone() {
        String rh = api.login("rh");
        for (JsonNode e : api.get("/api/employes?size=100", rh).get("contenu")) {
            assertThat(api.get("/api/employes/" + e.get("id").asLong(), rh).get("telephone").asString()).startsWith("+216");
        }
    }

    @Test
    @DisplayName("Démo : une entreprise avec une vraie hiérarchie, ajoutée une seule fois même si l'application redémarre")
    void demo_shouldAddAHierarchyOnce() {
        demo.ajouter();
        demo.ajouter();   // a restart adds nobody twice

        String rh = api.login("rh");
        JsonNode noeuds = api.get("/api/organigramme", rh).json();
        assertThat(noeuds.size()).isEqualTo(4 + DemoEmployesInitializer.NOMBRE);

        Map<Long, Integer> equipes = new HashMap<>();
        long sansManager = 0;
        for (JsonNode n : noeuds) {
            if (n.get("managerId").isNull()) sansManager++;
            else equipes.merge(n.get("managerId").asLong(), 1, Integer::sum);
        }
        assertThat(sansManager).isEqualTo(1);              // everyone leads up to the director
        assertThat(equipes.size()).isGreaterThanOrEqualTo(5); // several teams, at several levels
        for (JsonNode n : noeuds) {
            assertThat(api.get("/api/employes/" + n.get("id").asLong(), rh).get("telephone").asString()).startsWith("+216");
        }
        assertThat(api.get("/api/departements", rh).json().valueStream().map(d -> d.get("nom").asString())).contains("Commercial");
    }
}
