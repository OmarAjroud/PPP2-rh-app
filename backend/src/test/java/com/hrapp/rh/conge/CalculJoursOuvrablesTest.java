package com.hrapp.rh.conge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculJoursOuvrablesTest {

    private static final Set<DayOfWeek> WEEKEND = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    private static final Set<LocalDate> AUCUN_FERIE = Set.of();

    @Test
    @DisplayName("Une semaine complète du lundi au vendredi compte 5 jours")
    void compter_shouldReturnFive_forAFullWorkingWeek() {
        int jours = CalculJoursOuvrables.compter(
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 13), WEEKEND, AUCUN_FERIE);

        assertThat(jours).isEqualTo(5);
    }

    @Test
    @DisplayName("Du lundi au lundi suivant : le week-end n'est pas compté (6 jours)")
    void compter_shouldSkipTheWeekend() {
        int jours = CalculJoursOuvrables.compter(
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16), WEEKEND, AUCUN_FERIE);

        assertThat(jours).isEqualTo(6);
    }

    @Test
    @DisplayName("Un jour férié en semaine n'est pas compté")
    void compter_shouldSkipAHolidayOnAWorkingDay() {
        // Le jeudi 9 avril 2026 est la Journée des Martyrs
        int jours = CalculJoursOuvrables.compter(
                LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 10), WEEKEND,
                Set.of(LocalDate.of(2026, 4, 9)));

        assertThat(jours).isEqualTo(4);
    }

    @Test
    @DisplayName("Un jour férié un samedi n'est pas retiré une seconde fois")
    void compter_shouldNotSubtractTwice_whenHolidayFallsOnTheWeekend() {
        int jours = CalculJoursOuvrables.compter(
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15), WEEKEND,
                Set.of(LocalDate.of(2026, 11, 14)));

        assertThat(jours).isEqualTo(5);
    }

    @Test
    @DisplayName("Un seul jour de week-end compte 0")
    void compter_shouldReturnZero_forASingleWeekendDay() {
        int jours = CalculJoursOuvrables.compter(
                LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 14), WEEKEND, AUCUN_FERIE);

        assertThat(jours).isZero();
    }

    @Test
    @DisplayName("Un seul jour ouvrable compte 1")
    void compter_shouldReturnOne_forASingleWorkingDay() {
        int jours = CalculJoursOuvrables.compter(
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10), WEEKEND, AUCUN_FERIE);

        assertThat(jours).isEqualTo(1);
    }

    @Test
    @DisplayName("Si seul le dimanche est un jour de repos, le samedi compte")
    void compter_shouldCountSaturday_whenOnlySundayIsRestDay() {
        int jours = CalculJoursOuvrables.compter(
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 14), EnumSet.of(DayOfWeek.SUNDAY), AUCUN_FERIE);

        assertThat(jours).isEqualTo(6);
    }

    @Test
    @DisplayName("Une fin avant le début est refusée")
    void compter_shouldThrow_whenEndIsBeforeStart() {
        assertThatThrownBy(() -> CalculJoursOuvrables.compter(
                LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 9), WEEKEND, AUCUN_FERIE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}