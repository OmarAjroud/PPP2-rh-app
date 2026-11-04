package com.hrapp.rh.conge;

import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccesCongeTest {

    private final Employe karim = employe(3L, null);
    private final Employe lina = employe(4L, karim);          // l'équipe de Karim
    private final Employe sansManager = employe(5L, null);

    @Test
    @DisplayName("Équipe : le MANAGER direct de l'employé en est le manager d'équipe")
    void isTeamManager_shouldBeTrue_forTheDirectManager() {
        assertThat(AccesConge.isTeamManager(user(3L, Roles.MANAGER), lina)).isTrue();
    }

    @Test
    @DisplayName("Équipe : le manager d'une autre équipe ne l'est pas")
    void isTeamManager_shouldBeFalse_forAnotherManager() {
        assertThat(AccesConge.isTeamManager(user(9L, Roles.MANAGER), lina)).isFalse();
    }

    @Test
    @DisplayName("Équipe : un employé désigné manager mais sans le rôle MANAGER ne l'est pas")
    void isTeamManager_shouldBeFalse_whenUserDoesNotHaveManagerRole() {
        assertThat(AccesConge.isTeamManager(user(3L, Roles.EMPLOYE), lina)).isFalse();
    }

    @Test
    @DisplayName("Équipe : un employé sans manager n'a pas de manager d'équipe")
    void isTeamManager_shouldBeFalse_whenEmployeeHasNoManager() {
        assertThat(AccesConge.isTeamManager(user(3L, Roles.MANAGER), sansManager)).isFalse();
    }

    @Test
    @DisplayName("Consultation : un employé voit ses propres congés")
    void canView_shouldBeTrue_forTheOwner() {
        assertThat(AccesConge.canView(user(4L, Roles.EMPLOYE), lina)).isTrue();
    }

    @Test
    @DisplayName("Consultation : ADMIN et RH voient les congés de tout le monde")
    void canView_shouldBeTrue_forAdminAndRh() {
        assertThat(AccesConge.canView(user(1L, Roles.ADMIN), lina)).isTrue();
        assertThat(AccesConge.canView(user(2L, Roles.RH), lina)).isTrue();
    }

    @Test
    @DisplayName("Consultation : le manager d'équipe voit les congés de son équipe")
    void canView_shouldBeTrue_forTheTeamManager() {
        assertThat(AccesConge.canView(user(3L, Roles.MANAGER), lina)).isTrue();
    }

    @Test
    @DisplayName("Consultation : un collègue ou un autre manager n'y a pas accès")
    void canView_shouldBeFalse_forUnrelatedUsers() {
        assertThat(AccesConge.canView(user(7L, Roles.EMPLOYE), lina)).isFalse();
        assertThat(AccesConge.canView(user(9L, Roles.MANAGER), lina)).isFalse();
    }

    private Employe employe(Long id, Employe manager) {
        Employe e = new Employe();
        e.setId(id);
        e.setManager(manager);
        return e;
    }

    private UtilisateurConnecte user(Long employeId, String role) {
        return new UtilisateurConnecte(employeId, employeId, 1L, "test", "hash", role, true);
    }
}