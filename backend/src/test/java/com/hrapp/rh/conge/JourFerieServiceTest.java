package com.hrapp.rh.conge;

import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JourFerieServiceTest {

    @Mock private JourFerieRepository repository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC);

    private JourFerieService service;

    @BeforeEach
    void setUp() {
        service = new JourFerieService(repository, clock);
    }

    @Test
    @DisplayName("Création : refusée si un jour férié existe déjà à cette date")
    void create_shouldThrowConflict_whenDateAlreadyExists() {
        given(repository.existsByDateFerie(LocalDate.of(2026, 12, 17))).willReturn(true);

        assertThatThrownBy(() -> service.create(new JourFerieRequest(LocalDate.of(2026, 12, 17), "Doublon")))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("existe déjà");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Création : refusée hors 2000-2100, sinon le jour ne pourrait plus être consulté ni supprimé")
    void create_shouldRejectYearOutsideReadableRange() {
        assertThatThrownBy(() -> service.create(new JourFerieRequest(LocalDate.of(2206, 5, 20), "Faute de frappe")))
                .isInstanceOf(com.hrapp.rh.common.exception.RequeteInvalideException.class)
                .hasMessageContaining("2000");
        assertThatThrownBy(() -> service.create(new JourFerieRequest(LocalDate.of(1999, 1, 1), "Trop ancien")))
                .isInstanceOf(com.hrapp.rh.common.exception.RequeteInvalideException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Création : enregistre le jour férié quand la date est libre")
    void create_shouldSaveHoliday_whenDateIsFree() {
        given(repository.existsByDateFerie(LocalDate.of(2026, 12, 24))).willReturn(false);
        given(repository.save(any(JourFerie.class))).willAnswer(invocation -> {
            JourFerie saved = invocation.getArgument(0);
            saved.setId(30L);
            return saved;
        });

        JourFerieResponse result = service.create(new JourFerieRequest(LocalDate.of(2026, 12, 24), "  Jour spécial  "));

        assertThat(result.id()).isEqualTo(30L);
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 12, 24));
        assertThat(result.libelle()).isEqualTo("Jour spécial");
    }

    @Test
    @DisplayName("Suppression : 404 si le jour férié n'existe pas")
    void delete_shouldThrowNotFound_whenHolidayDoesNotExist() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    @DisplayName("Liste : sans année précisée, renvoie les jours fériés de l'année en cours")
    void findByYear_shouldUseCurrentYear_whenYearIsNotGiven() {
        JourFerie revolution = new JourFerie();
        revolution.setId(8L);
        revolution.setDateFerie(LocalDate.of(2026, 12, 17));
        revolution.setLibelle("Fête de la Révolution et de la Jeunesse");
        given(repository.findByDateFerieBetweenOrderByDateFerie(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .willReturn(List.of(revolution));

        List<JourFerieResponse> result = service.findByYear(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 12, 17));
    }
}