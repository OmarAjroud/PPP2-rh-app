package com.hrapp.rh.integration;

import com.hrapp.rh.conge.RelanceDemandesConge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** « Le manager d'abord, les RH en recours », de bout en bout ; relance automatique ; interruption par les RH. */
class DecisionCongesIT extends IntegrationTest {

    @Autowired private RelanceDemandesConge relance;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("Demande de Lina : seul Karim décide ; RH reçoit 403 (avec le nom du manager) et ne la voit pas « à décider »")
    void teamRequest_shouldBeDecidedByManagerOnly() {
        String lina = api.login("employe");
        String karim = api.login("manager");
        String rh = api.login("rh");
        long id = request(lina, mondayNextYear(1)).get("id").asLong();

        Api.Reponse refusRh = api.post("/api/conges/demandes/" + id + "/validation", rh, Map.of());
        assertThat(refusRh.statut()).isEqualTo(403);
        assertThat(refusRh.get("message").asString()).contains("Karim Mansour");

        assertThat(total(api.get("/api/conges/demandes/a-decider", karim))).isEqualTo(1);
        assertThat(total(api.get("/api/conges/demandes/a-decider", rh))).isZero();
        assertThat(total(api.get("/api/conges/demandes?statut=EN_ATTENTE", rh))).isEqualTo(1);    // vue globale

        assertThat(api.post("/api/conges/demandes/" + id + "/validation", karim, Map.of()).statut()).isEqualTo(200);
    }

    @Test
    @DisplayName("Sans décision du manager au bout de 3 jours : transmise aux RH une seule fois, RH prévenus et peuvent décider")
    void overdueRequest_shouldBeEscalatedOnce_andDecidableByHr() {
        String lina = api.login("employe");
        String rh = api.login("rh");
        String karim = api.login("manager");
        long id = request(lina, mondayNextYear(2)).get("id").asLong();
        api.post("/api/notifications/lecture", karim, null);
        jdbc.update("UPDATE demandes_conge SET date_creation = date_creation - INTERVAL 4 DAY WHERE id = ?", id);

        assertThat(relance.escalateOverdueRequests()).isEqualTo(1);
        assertThat(relance.escalateOverdueRequests()).isZero();                       // une seule fois

        assertThat(firstUnread(rh).get("type").asString()).isEqualTo("CONGE_A_TRAITER");
        assertThat(firstUnread(karim).get("titre").asString()).contains("Rappel");
        assertThat(total(api.get("/api/conges/demandes/a-decider", rh))).isEqualTo(1);
        Api.Reponse demande = api.get("/api/conges/demandes/" + id, rh);
        assertThat(demande.get("dateEscalade").isNull()).isFalse();

        Api.Reponse validation = api.post("/api/conges/demandes/" + id + "/validation", rh, Map.of());
        assertThat(validation.statut()).isEqualTo(200);
        assertThat(validation.get("decideParNomComplet").asString()).isEqualTo("Sara Trabelsi");
    }

    @Test
    @DisplayName("Karim n'a pas de manager : sa demande va directement aux RH (notification immédiate, pas de relance)")
    void requestWithoutManager_shouldGoStraightToHr() {
        String karim = api.login("manager");
        String rh = api.login("rh");
        long id = request(karim, mondayNextYear(3)).get("id").asLong();

        assertThat(firstUnread(rh).get("type").asString()).isEqualTo("CONGE_DEMANDE");
        assertThat(total(api.get("/api/conges/demandes/a-decider", rh))).isEqualTo(1);
        jdbc.update("UPDATE demandes_conge SET date_creation = date_creation - INTERVAL 4 DAY WHERE id = ?", id);
        assertThat(relance.escalateOverdueRequests()).isZero();                       // déjà chez les RH
        assertThat(api.post("/api/conges/demandes/" + id + "/validation", rh, Map.of()).statut()).isEqualTo(200);
    }

    @Test
    @DisplayName("Compte du manager désactivé : les demandes de son équipe vont aux RH, qui peuvent décider tout de suite")
    void deactivatedManager_shouldSendTeamRequestsToHr() {
        String rh = api.login("rh");
        assertThat(api.post("/api/employes/3/compte/desactivation", rh, null).statut()).isEqualTo(200);

        long id = request(api.login("employe"), mondayNextYear(4)).get("id").asLong();

        assertThat(firstUnread(rh).get("type").asString()).isEqualTo("CONGE_DEMANDE");
        assertThat(api.post("/api/conges/demandes/" + id + "/validation", rh, Map.of()).statut()).isEqualTo(200);
    }

    @Test
    @DisplayName("Interruption par les RH d'un congé approuvé pas encore commencé : annulé, solde rendu, Lina prévenue")
    void hrInterruption_shouldCancelAndRefund() {
        String lina = api.login("employe");
        String rh = api.login("rh");
        LocalDate lundi = mondayNextYear(5);
        long id = request(lina, lundi).get("id").asLong();
        api.post("/api/conges/demandes/" + id + "/validation", api.login("manager"), Map.of());

        assertThat(api.post("/api/conges/demandes/" + id + "/interruption", rh, Map.of("motif", " ")).statut()).isEqualTo(400);
        assertThat(api.post("/api/conges/demandes/" + id + "/interruption", lina, Map.of("motif", "x")).statut()).isEqualTo(403);
        Api.Reponse interruption = api.post("/api/conges/demandes/" + id + "/interruption", rh,
                Map.of("motif", "Besoin urgent sur le projet"));

        assertThat(interruption.statut()).isEqualTo(200);
        assertThat(interruption.get("statut").asString()).isEqualTo("ANNULEE");
        assertThat(interruption.get("motifInterruption").asString()).isEqualTo("Besoin urgent sur le projet");
        JsonNode solde = api.get("/api/conges/soldes/moi?annee=" + lundi.getYear(), lina).json().get(0);
        assertThat(solde.get("joursPris").asInt()).isZero();
        assertThat(firstUnread(lina).get("type").asString()).isEqualTo("CONGE_INTERROMPU");
    }

    // ---------- méthodes utilitaires ----------

    private Api.Reponse request(String jeton, LocalDate lundi) {
        Api.Reponse reponse = api.post("/api/conges/demandes", jeton, Map.of("typeCongeId", 1,
                "dateDebut", lundi.toString(), "dateFin", lundi.plusDays(1).toString()));
        assertThat(reponse.statut()).isEqualTo(201);
        return reponse;
    }

    private JsonNode firstUnread(String jeton) {
        return api.get("/api/notifications?nonLues=true", jeton).get("contenu").get(0);
    }

    private static long total(Api.Reponse page) {
        return page.get("totalElements").asLong();
    }
}
