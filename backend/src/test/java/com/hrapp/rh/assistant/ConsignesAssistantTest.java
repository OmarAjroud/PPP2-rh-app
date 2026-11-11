package com.hrapp.rh.assistant;

import com.hrapp.rh.departement.DepartementResponse;
import com.hrapp.rh.departement.DepartementService;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ConsignesAssistantTest {

    @Mock private DepartementService departementService;

    private final UtilisateurConnecte karim = new UtilisateurConnecte(3L, 3L, 1L, "manager", "hash", Roles.MANAGER, true);

    @Test
    @DisplayName("Contexte : dates de la semaine, de la semaine prochaine et du mois déjà calculées")
    void build_shouldGiveReadyToUseDates() {
        given(departementService.findAll()).willReturn(List.of());
        // jeudi 5 novembre 2026
        String consignes = consignes("2026-11-05T10:00:00Z").build(karim);

        assertThat(consignes)
                .contains("Aujourd'hui : jeudi 2026-11-05")
                .contains("Cette semaine : du 2026-11-02 au 2026-11-06")
                .contains("Semaine prochaine : du 2026-11-09 au 2026-11-13")
                .contains("Mois en cours : du 2026-11-01 au 2026-11-30");
    }

    @Test
    @DisplayName("Contexte : un lundi est bien le début de « cette semaine »")
    void build_shouldStartWeekOnMonday_evenOnMonday() {
        given(departementService.findAll()).willReturn(List.of());

        assertThat(consignes("2026-11-09T08:00:00Z").build(karim))
                .contains("Cette semaine : du 2026-11-09 au 2026-11-13")
                .contains("Semaine prochaine : du 2026-11-16 au 2026-11-20");
    }

    @Test
    @DisplayName("Contexte : l'utilisateur, son rôle et la liste des départements (identifiant = nom)")
    void build_shouldListUserAndDepartments() {
        given(departementService.findAll()).willReturn(List.of(
                new DepartementResponse(1L, "Informatique", null, 3L),
                new DepartementResponse(2L, "Ressources Humaines", null, 1L)));

        assertThat(consignes("2026-11-05T10:00:00Z").build(karim))
                .contains("« manager », rôle MANAGER, employé n°3, département n°1")
                .contains("Départements : 1 = Informatique, 2 = Ressources Humaines")
                .contains("dinars tunisiens")   // salaries are never read as euros
                .contains("lecture seule")
                .contains("Ne décide jamais toi-même qu'une information est inaccessible");
    }

    private ConsignesAssistant consignes(String instant) {
        return new ConsignesAssistant(departementService, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
