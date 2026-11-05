package com.hrapp.rh.conge;

import com.hrapp.rh.common.exception.RequeteInvalideException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class JoursOuvrablesServiceTest {

    @Mock private JourFerieRepository repository;

    private JoursOuvrablesService service;

    @BeforeEach
    void setUp() {
        service = new JoursOuvrablesService(repository, new CongeProperties(null, null));   // week-end par défaut
    }

    @Test
    @DisplayName("Comptage : les jours fériés lus en base sont exclus")
    void compter_shouldExcludeHolidaysFromTheDatabase() {
        JourFerie martyrs = new JourFerie();
        martyrs.setDateFerie(LocalDate.of(2026, 4, 9));
        martyrs.setLibelle("Journée des Martyrs");
        given(repository.findByDateFerieBetweenOrderByDateFerie(LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 10)))
                .willReturn(List.of(martyrs));

        JoursOuvrablesResponse result = service.calculate(LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 10));

        assertThat(result.joursOuvrables()).isEqualTo(4);
        assertThat(result.joursCalendaires()).isEqualTo(5);
    }

    @Test
    @DisplayName("Comptage : refusé si la fin précède le début")
    void compter_shouldThrow_whenEndIsBeforeStart() {
        assertThatThrownBy(() -> service.compter(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 9)))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("postérieure");

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Comptage : refusé au-delà de 366 jours")
    void compter_shouldThrow_whenPeriodIsTooLong() {
        assertThatThrownBy(() -> service.compter(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 12, 31)))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("366");

        verifyNoInteractions(repository);
    }
}