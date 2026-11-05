package com.hrapp.rh.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Authentification, rôles et visibilité des données, de bout en bout (filtre JWT + @PreAuthorize + services). */
class SecuriteIT extends IntegrationTest {

    @Test
    @DisplayName("Sans jeton : 401 au format d'erreur commun de l'API")
    void request_withoutToken_shouldReturn401WithApiErrorFormat() {
        Api.Reponse reponse = api.get("/api/employes", null);

        assertThat(reponse.statut()).isEqualTo(401);
        assertThat(reponse.get("statut").asInt()).isEqualTo(401);
        assertThat(reponse.get("chemin").asString()).isEqualTo("/api/employes");
    }

    @Test
    @DisplayName("Connexion : mauvais mot de passe 401, et l'échec est journalisé")
    void login_withWrongPassword_shouldReturn401_andBeAudited() {
        Api.Reponse reponse = api.post("/api/auth/login", null, Map.of("nomUtilisateur", "rh", "motDePasse", "faux"));

        assertThat(reponse.statut()).isEqualTo(401);
        Api.Reponse audit = api.get("/api/admin/audit?action=ECHEC_CONNEXION", api.login("admin"));
        assertThat(audit.get("contenu").get(0).get("nomUtilisateur").asString()).isEqualTo("rh");
    }

    @Test
    @DisplayName("Rafraîchissement : un jeton de rafraîchissement donne un nouveau jeton d'accès valable")
    void refresh_shouldIssueWorkingAccessToken() {
        Api.Reponse login = api.post("/api/auth/login", null, Map.of("nomUtilisateur", "employe", "motDePasse", Api.MOT_DE_PASSE));

        Api.Reponse refresh = api.post("/api/auth/refresh", null,
                Map.of("refreshToken", login.get("refreshToken").asString()));

        assertThat(refresh.statut()).isEqualTo(200);
        assertThat(api.get("/api/auth/me", refresh.get("accessToken").asString()).get("role").asString()).isEqualTo("EMPLOYE");
    }

    @Test
    @DisplayName("Changement de mot de passe : les anciens jetons sont refusés, l'ancien mot de passe aussi")
    void changePassword_shouldRevokeOldTokens() {
        String ancien = api.login("employe");

        Api.Reponse change = api.put("/api/auth/mot-de-passe", ancien,
                Map.of("motDePasseActuel", Api.MOT_DE_PASSE, "nouveauMotDePasse", "Nouveau2026!"));

        assertThat(change.statut()).isEqualTo(200);
        assertThat(api.get("/api/auth/me", ancien).statut()).isEqualTo(401);
        assertThat(api.get("/api/auth/me", change.get("accessToken").asString()).statut()).isEqualTo(200);
        assertThat(api.post("/api/auth/login", null, Map.of("nomUtilisateur", "employe", "motDePasse", Api.MOT_DE_PASSE))
                .statut()).isEqualTo(401);
    }

    @Test
    @DisplayName("Rôles : seul l'ADMIN les gère (RH 403) ; tout le monde peut les lire")
    void roles_shouldOnlyBeManagedByAdmin() {
        Map<String, String> role = Map.of("nom", "STAGIAIRE", "description", "Stagiaire");

        assertThat(api.post("/api/roles", api.login("rh"), role).statut()).isEqualTo(403);
        assertThat(api.post("/api/roles", api.login("employe"), role).statut()).isEqualTo(403);
        assertThat(api.post("/api/roles", api.login("admin"), role).statut()).isEqualTo(201);
        assertThat(api.get("/api/roles", api.login("employe")).json().size()).isEqualTo(5);
    }

    @Test
    @DisplayName("Liste des employés : ADMIN/RH tous, MANAGER son département, EMPLOYE lui-même")
    void employeeList_shouldBeScopedByRole() {
        assertThat(total(api.get("/api/employes", api.login("rh")))).isEqualTo(4);
        assertThat(total(api.get("/api/employes", api.login("manager")))).isEqualTo(3);   // Informatique
        assertThat(total(api.get("/api/employes", api.login("employe")))).isEqualTo(1);
    }

    @Test
    @DisplayName("Salaire : visible des RH et de l'intéressé, masqué pour le manager")
    void salary_shouldOnlyBeVisibleToHrAndOwner() {
        assertThat(api.get("/api/employes/4", api.login("rh")).get("salaire").asDouble()).isEqualTo(2500.0);
        assertThat(api.get("/api/employes/me", api.login("employe")).get("salaire").asDouble()).isEqualTo(2500.0);
        assertThat(api.get("/api/employes/4", api.login("manager")).get("salaire").isNull()).isTrue();
        assertThat(api.get("/api/employes/3", api.login("employe")).statut()).isEqualTo(403);
    }

    @Test
    @DisplayName("Recherche : « Lina Gharbi » (prénom + nom) trouve l'employée, dans les deux ordres")
    void search_withFullName_shouldMatchEachWord() {
        String rh = api.login("rh");

        assertThat(total(api.get("/api/employes?recherche=Lina Gharbi", rh))).isEqualTo(1);
        assertThat(total(api.get("/api/employes?recherche=gharbi lina", rh))).isEqualTo(1);
        assertThat(total(api.get("/api/employes?recherche=Lina Mansour", rh))).isZero();
    }

    @Test
    @DisplayName("Journal d'audit : réservé à l'ADMIN")
    void audit_shouldBeAdminOnly() {
        assertThat(api.get("/api/admin/audit", api.login("rh")).statut()).isEqualTo(403);
        assertThat(api.get("/api/admin/audit", api.login("admin")).statut()).isEqualTo(200);
    }

    private static long total(Api.Reponse page) {
        return page.get("totalElements").asLong();
    }
}
