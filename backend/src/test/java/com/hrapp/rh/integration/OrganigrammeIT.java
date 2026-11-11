package com.hrapp.rh.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Organigramme : réservé aux ADMIN et RH, chaque employé avec son manager, jamais le salaire. */
class OrganigrammeIT extends IntegrationTest {

    @Test
    @DisplayName("RH : tout le monde, avec le manager, le rôle et le département, sans salaire")
    void hr_shouldGetEveryoneWithTheirManager() {
        String rh = api.login("rh");
        Api.Reponse r = api.get("/api/organigramme", rh);

        assertThat(r.statut()).isEqualTo(200);
        long total = api.get("/api/employes?size=1", rh).get("totalElements").asLong();
        assertThat(r.json().size()).isEqualTo((int) total);

        Map<String, JsonNode> parEmail = new HashMap<>();
        r.json().forEach(n -> parEmail.put(n.get("email").asString(), n));
        JsonNode lina = parEmail.get("employe@rh.com");
        JsonNode karim = parEmail.get("manager@rh.com");
        assertThat(lina.get("managerId").asLong()).isEqualTo(karim.get("id").asLong());
        assertThat(karim.get("managerId").isNull()).isTrue();
        assertThat(lina.get("prenom").asString()).isEqualTo("Lina");
        assertThat(lina.get("roleNom").asString()).isEqualTo("EMPLOYE");
        assertThat(lina.get("departementNom").asString()).isEqualTo("Informatique");
        assertThat(lina.has("salaire")).isFalse();
    }

    @Test
    @DisplayName("Réservé aux ADMIN et RH")
    void others_shouldBeRefused() {
        assertThat(api.get("/api/organigramme", api.login("admin")).statut()).isEqualTo(200);
        assertThat(api.get("/api/organigramme", api.login("manager")).statut()).isEqualTo(403);
        assertThat(api.get("/api/organigramme", api.login("employe")).statut()).isEqualTo(403);
        assertThat(api.get("/api/organigramme", null).statut()).isEqualTo(401);
    }
}
