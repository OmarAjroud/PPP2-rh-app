package com.hrapp.rh.integration;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** Cycle de vie complet d'une demande de congé, sur une vraie base : verrous, soldes, historique, notifications, emails. */
class CongeWorkflowIT extends IntegrationTest {

    private static final int ANNUEL = 1;
    private static final int MALADIE = 2;

    @Test
    @DisplayName("Demande → notification du manager → validation → solde déduit, historique, notification et email du demandeur")
    void approvalFlow_shouldDeductBalanceAndNotifyEveryone() {
        String lina = api.login("employe");
        String karim = api.login("manager");
        LocalDate lundi = mondayNextYear(1);
        int annee = lundi.getYear();
        int joursOuvrables = api.get("/api/conges/demandes/jours-ouvrables?dateDebut=" + lundi
                + "&dateFin=" + lundi.plusDays(2), lina).get("joursOuvrables").asInt();

        Api.Reponse demande = request(lina, ANNUEL, lundi, lundi.plusDays(2));
        assertThat(demande.statut()).isEqualTo(201);
        assertThat(demande.get("statut").asString()).isEqualTo("EN_ATTENTE");
        assertThat(demande.get("nombreJours").asInt()).isEqualTo(joursOuvrables);
        long id = demande.get("id").asLong();

        // les jours sont réservés tant que la demande est en attente
        JsonNode solde = annualBalance(lina, annee);
        assertThat(solde.get("joursEnAttente").asInt()).isEqualTo(joursOuvrables);
        assertThat(solde.get("joursDisponibles").asInt()).isEqualTo(22 - joursOuvrables);

        // le manager direct est notifié (et lui seul)
        assertThat(unread(karim)).isEqualTo(1);
        assertThat(api.get("/api/notifications", karim).get("contenu").get(0).get("type").asString()).isEqualTo("CONGE_DEMANDE");
        assertThat(unread(api.login("rh"))).isZero();

        // validation
        Api.Reponse validation = api.post("/api/conges/demandes/" + id + "/validation", karim, Map.of("motif", "Bon repos"));
        assertThat(validation.statut()).isEqualTo(200);
        assertThat(validation.get("statut").asString()).isEqualTo("APPROUVEE");
        assertThat(validation.get("decideParNomComplet").asString()).isEqualTo("Karim Mansour");

        solde = annualBalance(lina, annee);
        assertThat(solde.get("joursPris").asInt()).isEqualTo(joursOuvrables);
        assertThat(solde.get("joursEnAttente").asInt()).isZero();
        assertThat(solde.get("joursDisponibles").asInt()).isEqualTo(22 - joursOuvrables);

        // l'historique totalise toujours « alloué - pris »
        List<String> mouvements = types(api.get("/api/conges/soldes/moi/mouvements?annee=" + annee, lina).json());
        assertThat(mouvements).containsExactly("DEDUCTION", "ALLOCATION");          // le plus récent d'abord

        Api.Reponse notificationsLina = api.get("/api/notifications", lina);
        assertThat(notificationsLina.get("contenu").get(0).get("type").asString()).isEqualTo("CONGE_APPROUVE");
        assertThat(notificationsLina.get("contenu").get(0).get("message").asString()).contains("Bon repos");

        // emails : demande (manager) + décision (demandeur), envoyés après validation de la transaction
        verify(mailSender, timeout(5000).atLeast(2)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Annulation d'un congé approuvé avant son début : jours rendus, mouvement de remboursement, manager prévenu")
    void cancelApproved_shouldRefundAndNotifyManager() {
        String lina = api.login("employe");
        String karim = api.login("manager");
        LocalDate lundi = mondayNextYear(2);
        long id = request(lina, ANNUEL, lundi, lundi.plusDays(1)).get("id").asLong();
        api.post("/api/conges/demandes/" + id + "/validation", karim, null);
        api.post("/api/notifications/lecture", karim, null);                            // la cloche du manager à zéro

        Api.Reponse annulation = api.post("/api/conges/demandes/" + id + "/annulation", lina, null);

        assertThat(annulation.statut()).isEqualTo(200);
        assertThat(annulation.get("statut").asString()).isEqualTo("ANNULEE");
        JsonNode solde = annualBalance(lina, lundi.getYear());
        assertThat(solde.get("joursPris").asInt()).isZero();
        assertThat(solde.get("joursDisponibles").asInt()).isEqualTo(22);
        assertThat(types(api.get("/api/conges/soldes/moi/mouvements?annee=" + lundi.getYear(), lina).json()))
                .containsExactly("REMBOURSEMENT", "DEDUCTION", "ALLOCATION");
        assertThat(unread(karim)).isEqualTo(1);
        assertThat(api.get("/api/notifications?nonLues=true", karim).get("contenu").get(0).get("type").asString())
                .isEqualTo("CONGE_ANNULE");
    }

    @Test
    @DisplayName("Refus : motif obligatoire, solde inchangé, le demandeur voit le motif")
    void reject_shouldRequireReason_andLeaveBalanceUntouched() {
        String lina = api.login("employe");
        String karim = api.login("manager");
        LocalDate lundi = mondayNextYear(3);
        long id = request(lina, ANNUEL, lundi, lundi.plusDays(1)).get("id").asLong();

        assertThat(api.post("/api/conges/demandes/" + id + "/refus", karim, Map.of("motif", " ")).statut()).isEqualTo(400);
        Api.Reponse refus = api.post("/api/conges/demandes/" + id + "/refus", karim, Map.of("motif", "Clôture annuelle"));

        assertThat(refus.get("statut").asString()).isEqualTo("REFUSEE");
        assertThat(annualBalance(lina, lundi.getYear()).get("joursDisponibles").asInt()).isEqualTo(22);
        assertThat(api.get("/api/notifications", lina).get("contenu").get(0).get("message").asString())
                .contains("Clôture annuelle");
    }

    @Test
    @DisplayName("Règles : chevauchement 409, solde insuffisant 409, pas de validation de sa propre demande, ni par un employé")
    void businessRules_shouldBeEnforcedThroughTheApi() {
        String lina = api.login("employe");
        String rh = api.login("rh");
        LocalDate lundi = mondayNextYear(4);
        int annee = lundi.getYear();
        long id = request(lina, ANNUEL, lundi, lundi.plusDays(2)).get("id").asLong();

        // chevauchement avec la demande en attente
        assertThat(request(lina, ANNUEL, lundi.plusDays(1), lundi.plusDays(3)).statut()).isEqualTo(409);

        // solde insuffisant : les RH ramènent l'allocation au strict nécessaire, puis une nouvelle demande dépasse
        int reserve = annualBalance(lina, annee).get("joursEnAttente").asInt();
        Api.Reponse ajustement = api.post("/api/conges/soldes/4/ajustements", rh, Map.of("typeCongeId", ANNUEL,
                "annee", annee, "jours", -(22 - reserve), "commentaire", "Régularisation"));
        assertThat(ajustement.statut()).isEqualTo(201);
        Api.Reponse refusSolde = request(lina, ANNUEL, lundi.plusWeeks(2), lundi.plusWeeks(2).plusDays(1));
        assertThat(refusSolde.statut()).isEqualTo(409);
        assertThat(refusSolde.get("message").asString()).contains("Solde insuffisant");

        // un employé ne valide rien (403), le congé maladie ne consomme pas de solde
        assertThat(api.post("/api/conges/demandes/" + id + "/validation", lina, null).statut()).isEqualTo(403);
        assertThat(request(lina, MALADIE, lundi.plusWeeks(3), lundi.plusWeeks(3)).statut()).isEqualTo(201);

        // personne ne valide sa propre demande, même RH
        long propre = request(rh, ANNUEL, lundi, lundi).get("id").asLong();
        assertThat(api.post("/api/conges/demandes/" + propre + "/validation", rh, null).statut()).isEqualTo(403);
    }

    @Test
    @DisplayName("Visibilité : le demandeur, son manager d'équipe et ADMIN/RH ; un manager hors équipe reçoit 404")
    void request_shouldOnlyBeVisibleToItsAudience() {
        String lina = api.login("employe");
        String karim = api.login("manager");
        long demandeLina = request(lina, ANNUEL, mondayNextYear(5), mondayNextYear(5)).get("id").asLong();
        long demandeSara = request(api.login("rh"), ANNUEL, mondayNextYear(5), mondayNextYear(5)).get("id").asLong();

        assertThat(api.get("/api/conges/demandes/" + demandeLina, lina).statut()).isEqualTo(200);
        assertThat(api.get("/api/conges/demandes/" + demandeLina, karim).statut()).isEqualTo(200);   // son équipe
        assertThat(api.get("/api/conges/demandes/" + demandeLina, api.login("admin")).statut()).isEqualTo(200);
        // Sara n'a pas de manager : Karim n'est pas son manager d'équipe, la demande n'existe pas pour lui
        assertThat(api.get("/api/conges/demandes/" + demandeSara, karim).statut()).isEqualTo(404);
        assertThat(api.post("/api/conges/demandes/" + demandeSara + "/validation", karim, null).statut()).isEqualTo(404);
        // Sara n'a pas de manager et elle est la seule RH : sa demande est notifiée aux administrateurs (Amine)
        assertThat(api.get("/api/notifications/non-lues/nombre", api.login("admin")).get("nombre").asLong()).isEqualTo(1);
    }

    // ---------- méthodes utilitaires ----------

    private Api.Reponse request(String jeton, int type, LocalDate debut, LocalDate fin) {
        return api.post("/api/conges/demandes", jeton, Map.of("typeCongeId", type,
                "dateDebut", debut.toString(), "dateFin", fin.toString(), "commentaire", "Test d'intégration"));
    }

    private JsonNode annualBalance(String jeton, int annee) {
        for (JsonNode solde : api.get("/api/conges/soldes/moi?annee=" + annee, jeton).json()) {
            if ("ANNUEL".equals(solde.get("typeCongeCode").asString())) {
                return solde;
            }
        }
        throw new AssertionError("Pas de solde annuel pour " + annee);
    }

    private long unread(String jeton) {
        return api.get("/api/notifications/non-lues/nombre", jeton).get("nombre").asLong();
    }

    private static List<String> types(JsonNode mouvements) {
        List<String> types = new ArrayList<>();
        mouvements.forEach(m -> types.add(m.get("typeMouvement").asString()));
        return types;
    }
}
