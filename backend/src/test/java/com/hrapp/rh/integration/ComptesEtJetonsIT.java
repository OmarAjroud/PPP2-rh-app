package com.hrapp.rh.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Désactivation des comptes, rotation des jetons, limites de débit, règles sur les fiches employés. */
class ComptesEtJetonsIT extends IntegrationTest {

    @Test
    @DisplayName("Départ : compte désactivé → jeton en cours refusé tout de suite, connexion refusée ; réactivation → accès rendu")
    void deactivation_shouldCutAccessImmediately_andReactivationRestoreIt() {
        String lina = api.login("employe");
        String rh = api.login("rh");

        Api.Reponse desactivation = api.post("/api/employes/4/compte/desactivation", rh, null);
        assertThat(desactivation.get("statut").asString()).isEqualTo("DESACTIVE");
        assertThat(api.get("/api/auth/me", lina).statut()).isEqualTo(401);
        assertThat(api.post("/api/auth/login", null, Map.of("nomUtilisateur", "employe", "motDePasse", Api.MOT_DE_PASSE))
                .statut()).isEqualTo(401);
        assertThat(api.post("/api/employes/4/compte/desactivation", rh, null).statut()).isEqualTo(409);   // déjà fait

        assertThat(api.post("/api/employes/4/compte/reactivation", rh, null).get("statut").asString()).isEqualTo("ACTIF");
        assertThat(api.login("employe")).isNotBlank();
    }

    @Test
    @DisplayName("Désactivation : un RH ne désactive ni son propre compte ni celui d'un ADMIN ; un employé rien du tout")
    void deactivation_shouldRespectRoles() {
        String rh = api.login("rh");

        assertThat(api.post("/api/employes/2/compte/desactivation", rh, null).statut()).isEqualTo(403);
        assertThat(api.post("/api/employes/1/compte/desactivation", rh, null).statut()).isEqualTo(403);
        assertThat(api.post("/api/employes/3/compte/desactivation", api.login("employe"), null).statut()).isEqualTo(403);
    }

    @Test
    @DisplayName("Rotation : un jeton de rafraîchissement ne sert qu'une fois ; réutilisé, toutes les sessions sont fermées")
    void refreshToken_shouldBeSingleUse_andReuseShouldCloseAllSessions() {
        Api.Reponse login = api.post("/api/auth/login", null, Map.of("nomUtilisateur", "employe", "motDePasse", Api.MOT_DE_PASSE));
        String premier = login.get("refreshToken").asString();

        Api.Reponse rafraichi = api.post("/api/auth/refresh", null, Map.of("refreshToken", premier));
        assertThat(rafraichi.statut()).isEqualTo(200);
        String nouvelAcces = rafraichi.get("accessToken").asString();
        String nouveauRafraichissement = rafraichi.get("refreshToken").asString();
        assertThat(api.get("/api/auth/me", nouvelAcces).statut()).isEqualTo(200);

        // l'ancien jeton revient (copie volée) : refusé, et les jetons du vrai utilisateur aussi
        assertThat(api.post("/api/auth/refresh", null, Map.of("refreshToken", premier)).statut()).isEqualTo(401);
        assertThat(api.get("/api/auth/me", nouvelAcces).statut()).isEqualTo(401);
        assertThat(api.post("/api/auth/refresh", null, Map.of("refreshToken", nouveauRafraichissement)).statut())
                .isEqualTo(401);
        assertThat(api.login("employe")).isNotBlank();                                // il suffit de se reconnecter
    }

    @Test
    @DisplayName("Déconnexion : le jeton de rafraîchissement est révoqué côté serveur ; « partout » ferme toutes les sessions")
    void logout_shouldRevokeTokens() {
        Api.Reponse login = api.post("/api/auth/login", null, Map.of("nomUtilisateur", "rh", "motDePasse", Api.MOT_DE_PASSE));
        String rafraichissement = login.get("refreshToken").asString();

        assertThat(api.post("/api/auth/logout", null, Map.of("refreshToken", rafraichissement)).statut()).isEqualTo(204);
        assertThat(api.post("/api/auth/refresh", null, Map.of("refreshToken", rafraichissement)).statut()).isEqualTo(401);

        String autreSession = api.login("rh");
        assertThat(api.post("/api/auth/deconnexion-partout", autreSession, null).statut()).isEqualTo(204);
        assertThat(api.get("/api/auth/me", autreSession).statut()).isEqualTo(401);
    }

    @Test
    @DisplayName("Password spraying : 20 échecs sur des comptes variés depuis une adresse → 429, même pour un autre compte")
    void manyFailuresAcrossAccounts_shouldBlockTheAddress() {
        for (int i = 0; i < 20; i++) {
            api.post("/api/auth/login", null, Map.of("nomUtilisateur", "compte" + i, "motDePasse", "Test1234!"));
        }

        assertThat(api.post("/api/auth/login", null, Map.of("nomUtilisateur", "admin", "motDePasse", Api.MOT_DE_PASSE))
                .statut()).isEqualTo(429);
    }

    @Test
    @DisplayName("Mot de passe oublié : 5 demandes par 15 minutes depuis une adresse, la 6e reçoit 429")
    void forgotPassword_shouldBeRateLimited() {
        for (int i = 0; i < 5; i++) {
            assertThat(api.post("/api/auth/mot-de-passe-oublie", null, Map.of("email", "x" + i + "@rh.com")).statut())
                    .isEqualTo(202);
        }
        assertThat(api.post("/api/auth/mot-de-passe-oublie", null, Map.of("email", "y@rh.com")).statut()).isEqualTo(429);
    }

    @Test
    @DisplayName("Fiches : un RH ne modifie pas la sienne ; le dernier ADMIN ne peut pas se rétrograder ; historique → 409 explicite")
    void employeeRules_shouldProtectRecords() {
        String rh = api.login("rh");
        String admin = api.login("admin");
        Map<String, Object> sara = new HashMap<>(Map.of("nom", "Trabelsi", "prenom", "Sara", "email", "rh@rh.com",
                "dateEmbauche", "2022-03-01", "salaire", 9999, "departementId", 2, "roleId", 2));
        assertThat(api.put("/api/employes/2", rh, sara).statut()).isEqualTo(403);

        Map<String, Object> amine = new HashMap<>(Map.of("nom", "Ben Salah", "prenom", "Amine", "email", "admin@rh.com",
                "dateEmbauche", "2022-01-10", "salaire", 5000, "departementId", 1, "roleId", 4));
        Api.Reponse retrogradation = api.put("/api/employes/1", admin, amine);
        assertThat(retrogradation.statut()).isEqualTo(409);
        assertThat(retrogradation.get("message").asString()).contains("dernier administrateur");

        // Lina a des soldes et des demandes : on ne la supprime pas, on désactive son compte
        api.post("/api/conges/demandes", api.login("employe"), Map.of("typeCongeId", 1,
                "dateDebut", mondayNextYear(6).toString(), "dateFin", mondayNextYear(6).toString()));
        Api.Reponse suppression = api.call(org.springframework.http.HttpMethod.DELETE, "/api/employes/4", rh, null,
                org.springframework.http.MediaType.APPLICATION_JSON);
        assertThat(suppression.statut()).isEqualTo(409);
        assertThat(suppression.get("message").asString()).contains("désactivez");
    }

    @Test
    @DisplayName("Manager : un membre de son équipe dans un autre département reste visible (fiche et liste)")
    void manager_shouldSeeDirectReportInAnotherDepartment() {
        Map<String, Object> linaEnFinance = new HashMap<>(Map.of("nom", "Gharbi", "prenom", "Lina", "email", "employe@rh.com",
                "dateEmbauche", "2023-09-01", "salaire", 2500, "departementId", 3, "roleId", 4, "managerId", 3));
        assertThat(api.put("/api/employes/4", api.login("rh"), linaEnFinance).statut()).isEqualTo(200);
        String karim = api.login("manager");

        assertThat(api.get("/api/employes/4", karim).statut()).isEqualTo(200);
        assertThat(api.get("/api/employes", karim).get("totalElements").asLong()).isEqualTo(3);   // Amine, Karim + Lina
    }
}
