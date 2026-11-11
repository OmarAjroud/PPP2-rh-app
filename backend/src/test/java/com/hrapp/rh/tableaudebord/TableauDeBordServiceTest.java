package com.hrapp.rh.tableaudebord;

import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.conge.StatutDemandeConge;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import com.hrapp.rh.tableaudebord.TableauDeBordResponse.MasseSalarialeDepartement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TableauDeBordServiceTest {

    @Mock private TableauDeBordRepository repository;

    // « Aujourd'hui » = jeudi 5 novembre 2026
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC);
    private final LocalDate aujourdhui = LocalDate.of(2026, 11, 5);

    private TableauDeBordService service() {
        return new TableauDeBordService(repository, clock);
    }

    @Test
    @DisplayName("RH : toute l'entreprise, indicateurs calculés sur le mois et le jour en cours")
    void build_shouldCoverWholeCompany_forHr() {
        given(repository.countEmployees(null)).willReturn(12L);
        given(repository.countRequests(null, StatutDemandeConge.EN_ATTENTE)).willReturn(3L);
        given(repository.countHires(null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).willReturn(2L);
        given(repository.countAbsent(null, aujourdhui, StatutDemandeConge.APPROUVEE)).willReturn(1L);

        TableauDeBordResponse result = service().build(user(Roles.RH), null, null);

        assertThat(result.annee()).isEqualTo(2026);
        assertThat(result.departementId()).isNull();
        assertThat(result.indicateurs()).isEqualTo(new TableauDeBordResponse.Indicateurs(12, 3, 2, 1));
        assertThat(result.masseSalariale()).isNotNull();
    }

    @Test
    @DisplayName("MANAGER : limité à son département, sans masse salariale (jamais interrogée)")
    void build_shouldScopeToOwnDepartment_andHidePayroll_forManager() {
        TableauDeBordResponse result = service().build(user(Roles.MANAGER), null, null);

        assertThat(result.departementId()).isEqualTo(1L);
        assertThat(result.masseSalariale()).isNull();
        verify(repository).countEmployees(1L);
        verify(repository).headcountByDepartment(1L);
        verify(repository, never()).payrollByDepartment(any());
        verify(repository, never()).countEmployees(isNull());
    }

    @Test
    @DisplayName("MANAGER : demander son propre département explicitement est accepté")
    void build_shouldAccept_whenManagerAsksForOwnDepartment() {
        assertThat(service().build(user(Roles.MANAGER), null, 1L).departementId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("MANAGER : le tableau de bord d'un autre département est refusé (403)")
    void build_shouldDenyManager_otherDepartment() {
        assertThatThrownBy(() -> service().build(user(Roles.MANAGER), null, 2L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("EMPLOYE : refusé (403), même si le contrôleur est contourné")
    void build_shouldDenyEmployee() {
        assertThatThrownBy(() -> service().build(user(Roles.EMPLOYE), null, null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("ADMIN : filtre sur un département existant")
    void build_shouldFilterByDepartment_forAdmin() {
        given(repository.departmentExists(2L)).willReturn(true);

        TableauDeBordResponse result = service().build(user(Roles.ADMIN), null, 2L);

        assertThat(result.departementId()).isEqualTo(2L);
        verify(repository).countEmployees(2L);
        verify(repository).payrollByDepartment(2L);
    }

    @Test
    @DisplayName("ADMIN : un département inexistant donne 404")
    void build_shouldThrowNotFound_whenDepartmentDoesNotExist() {
        given(repository.departmentExists(99L)).willReturn(false);

        assertThatThrownBy(() -> service().build(user(Roles.ADMIN), null, 99L))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    @DisplayName("Embauches par mois : les 12 mois sont présents, à 0 quand il n'y a pas d'embauche")
    void build_shouldFillAllTwelveMonths() {
        given(repository.hiresByMonth(null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .willReturn(List.of(new Object[]{3, 2L}, new Object[]{9, 1L}));

        var mois = service().build(user(Roles.RH), 2025, null).embauchesParMois();

        assertThat(mois).hasSize(12);
        assertThat(mois.get(0).nombre()).isZero();
        assertThat(mois.get(2)).isEqualTo(new TableauDeBordResponse.EmbauchesMois(3, 2));
        assertThat(mois.get(8)).isEqualTo(new TableauDeBordResponse.EmbauchesMois(9, 1));
        assertThat(mois.stream().mapToLong(TableauDeBordResponse.EmbauchesMois::nombre).sum()).isEqualTo(3);
    }

    @Test
    @DisplayName("Masse salariale : moyenne sur les seuls salaires renseignés, département vide à 0")
    void build_shouldComputeAverageOnKnownSalariesOnly() {
        given(repository.payrollByDepartment(null)).willReturn(List.of(
                // 3 employés, 2 salaires renseignés (5000 + 2500) : moyenne 3750, pas 2500
                new Object[]{1L, "Informatique", 3L, new BigDecimal("7500.00"), 2L},
                new Object[]{3L, "Finance", 0L, null, 0L}));

        List<MasseSalarialeDepartement> masse = service().build(user(Roles.ADMIN), null, null).masseSalariale();

        assertThat(masse.get(0).total()).isEqualByComparingTo("7500.00");
        assertThat(masse.get(0).moyenne()).isEqualByComparingTo("3750.00");
        assertThat(masse.get(0).nombreEmployes()).isEqualTo(3);
        assertThat(masse.get(1).total()).isEqualByComparingTo("0");
        assertThat(masse.get(1).moyenne()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Masse salariale en dinars : les millimes sont gardés (3 décimales), jamais arrondis au centime")
    void build_shouldKeepMillimes() {
        given(repository.payrollByDepartment(null)).willReturn(List.<Object[]>of(
                new Object[]{1L, "Informatique", 3L, new BigDecimal("7001.235"), 3L}));

        MasseSalarialeDepartement m = service().build(user(Roles.ADMIN), null, null).masseSalariale().get(0);

        assertThat(m.total()).isEqualTo(new BigDecimal("7001.235"));
        assertThat(m.moyenne()).isEqualTo(new BigDecimal("2333.745"));   // 7001.235 / 3, to the millime
    }

    @Test
    @DisplayName("Masse salariale : moyenne arrondie au millime (dinars)")
    void build_shouldRoundAverageToMillimes() {
        given(repository.payrollByDepartment(null)).willReturn(List.<Object[]>of(
                new Object[]{1L, "Informatique", 3L, new BigDecimal("10000.00"), 3L}));

        var moyenne = service().build(user(Roles.RH), null, null).masseSalariale().getFirst().moyenne();

        assertThat(moyenne).isEqualTo(new BigDecimal("3333.333"));
    }

    @Test
    @DisplayName("Année hors limites : 400")
    void build_shouldRejectInvalidYear() {
        assertThatThrownBy(() -> service().build(user(Roles.RH), 1999, null))
                .isInstanceOf(RequeteInvalideException.class);
        verify(repository, never()).countEmployees(anyLong());
    }

    private UtilisateurConnecte user(String role) {
        return new UtilisateurConnecte(9L, 9L, 1L, "test", "hash", role, true);
    }
}
