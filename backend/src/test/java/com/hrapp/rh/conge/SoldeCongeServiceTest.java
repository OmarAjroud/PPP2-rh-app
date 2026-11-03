package com.hrapp.rh.conge;

import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SoldeCongeServiceTest {

    private static final LocalDate DEBUT_2026 = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN_2026 = LocalDate.of(2026, 12, 31);

    @Mock private SoldeCongeRepository soldeRepository;
    @Mock private DemandeCongeRepository demandeRepository;
    @Mock private TypeCongeRepository typeCongeRepository;

    // « Aujourd'hui » = mardi 3 novembre 2026
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC);

    private SoldeCongeService service;

    @BeforeEach
    void setUp() {
        service = new SoldeCongeService(soldeRepository, demandeRepository, typeCongeRepository, clock);
    }

    @Test
    @DisplayName("Disponible : alloué moins pris moins réservé par les demandes en attente")
    void calculateAvailable_shouldSubtractTakenAndPendingDays_whenBalanceExists() {
        TypeConge annuel = annuel();
        Employe lina = employe(4L);
        given(soldeRepository.findByEmployeIdAndTypeCongeIdAndAnnee(4L, 1L, 2026))
                .willReturn(Optional.of(solde(lina, annuel, "22.0", "5.0")));
        given(demandeRepository.sumJours(4L, 1L, StatutDemandeConge.EN_ATTENTE, DEBUT_2026, FIN_2026))
                .willReturn(new BigDecimal("3.0"));

        BigDecimal disponible = service.calculateAvailable(lina, annuel, 2026);

        assertThat(disponible).isEqualByComparingTo("14");
    }

    @Test
    @DisplayName("Disponible : crée le solde de l'année avec la valeur par défaut du type s'il n'existe pas")
    void calculateAvailable_shouldCreateBalanceFromTypeDefault_whenNoneExists() {
        TypeConge annuel = annuel();
        Employe lina = employe(4L);
        given(soldeRepository.findByEmployeIdAndTypeCongeIdAndAnnee(4L, 1L, 2026)).willReturn(Optional.empty());
        given(soldeRepository.save(any(SoldeConge.class))).willAnswer(invocation -> invocation.getArgument(0));

        BigDecimal disponible = service.calculateAvailable(lina, annuel, 2026);

        assertThat(disponible).isEqualByComparingTo("22");
        ArgumentCaptor<SoldeConge> captor = ArgumentCaptor.forClass(SoldeConge.class);
        verify(soldeRepository).save(captor.capture());
        assertThat(captor.getValue().getJoursAlloues()).isEqualByComparingTo("22");
        assertThat(captor.getValue().getJoursPris()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getAnnee()).isEqualTo(2026);
        assertThat(captor.getValue().getEmploye()).isSameAs(lina);
    }

    @Test
    @DisplayName("Mes soldes : sans solde en base, valeur par défaut présentée sans rien créer")
    void findMine_shouldReturnDefaultsWithoutCreatingRows() {
        given(typeCongeRepository.findByDeduitDuSoldeTrueOrderByLibelle()).willReturn(List.of(annuel()));
        given(soldeRepository.findByEmployeIdAndTypeCongeIdAndAnnee(4L, 1L, 2026)).willReturn(Optional.empty());

        List<SoldeResponse> result = service.findMine(user(), null);   // année non précisée : 2026

        assertThat(result).hasSize(1);
        assertThat(result.get(0).annee()).isEqualTo(2026);
        assertThat(result.get(0).typeCongeCode()).isEqualTo("ANNUEL");
        assertThat(result.get(0).joursAlloues()).isEqualByComparingTo("22");
        assertThat(result.get(0).joursDisponibles()).isEqualByComparingTo("22");
        verify(soldeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mes soldes : les demandes en attente réduisent le disponible")
    void findMine_shouldReflectPendingRequests() {
        TypeConge annuel = annuel();
        given(typeCongeRepository.findByDeduitDuSoldeTrueOrderByLibelle()).willReturn(List.of(annuel));
        given(soldeRepository.findByEmployeIdAndTypeCongeIdAndAnnee(4L, 1L, 2026))
                .willReturn(Optional.of(solde(employe(4L), annuel, "22.0", "5.0")));
        given(demandeRepository.sumJours(4L, 1L, StatutDemandeConge.EN_ATTENTE, DEBUT_2026, FIN_2026))
                .willReturn(new BigDecimal("4.0"));

        SoldeResponse result = service.findMine(user(), 2026).get(0);

        assertThat(result.joursPris()).isEqualByComparingTo("5");
        assertThat(result.joursEnAttente()).isEqualByComparingTo("4");
        assertThat(result.joursDisponibles()).isEqualByComparingTo("13");
    }

    // ---------- méthodes utilitaires ----------

    private TypeConge annuel() {
        TypeConge t = new TypeConge();
        t.setId(1L);
        t.setCode("ANNUEL");
        t.setLibelle("Congé annuel");
        t.setDeduitDuSolde(true);
        t.setJoursParAn(new BigDecimal("22.0"));
        return t;
    }

    private Employe employe(Long id) {
        Employe e = new Employe();
        e.setId(id);
        return e;
    }

    private SoldeConge solde(Employe employe, TypeConge type, String alloues, String pris) {
        SoldeConge s = new SoldeConge();
        s.setEmploye(employe);
        s.setTypeConge(type);
        s.setAnnee(2026);
        s.setJoursAlloues(new BigDecimal(alloues));
        s.setJoursPris(new BigDecimal(pris));
        return s;
    }

    private UtilisateurConnecte user() {
        return new UtilisateurConnecte(4L, 4L, 1L, "employe", "hash", Roles.EMPLOYE, true);
    }
}