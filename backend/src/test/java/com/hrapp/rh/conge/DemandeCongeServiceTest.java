package com.hrapp.rh.conge;

import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.employe.EmployeRepository;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DemandeCongeServiceTest {

    @Mock private DemandeCongeRepository repository;
    @Mock private EmployeRepository employeRepository;
    @Mock private TypeCongeRepository typeCongeRepository;
    @Mock private SoldeCongeService soldeService;
    @Mock private JoursOuvrablesService joursOuvrablesService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Spy  private DemandeCongeMapper mapper = new DemandeCongeMapperImpl();

    // « Aujourd'hui » = mardi 3 novembre 2026
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC);

    private final Employe lina = employe(4L);
    private final TypeConge annuel = type(1L, "ANNUEL", true, false);
    private final TypeConge maladie = type(2L, "MALADIE", false, true);

    private DemandeCongeService service;

    @BeforeEach
    void setUp() {
        service = new DemandeCongeService(repository, employeRepository, typeCongeRepository,
                soldeService, joursOuvrablesService, mapper, clock, eventPublisher);
    }

    // ---------- création ----------

    @Test
    @DisplayName("Création : enregistre une demande en attente quand toutes les règles sont respectées")
    void create_shouldSavePendingRequest_whenAllRulesAreMet() {
        LocalDate lundi = LocalDate.of(2026, 11, 9);
        LocalDate vendredi = LocalDate.of(2026, 11, 13);
        givenEmployeeAndType(annuel);
        given(joursOuvrablesService.compter(lundi, vendredi)).willReturn(5);
        given(repository.existsByEmployeIdAndStatutInAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                eq(4L), anyCollection(), eq(vendredi), eq(lundi))).willReturn(false);
        given(soldeService.calculateAvailable(lina, annuel, 2026)).willReturn(new BigDecimal("22.0"));
        given(repository.save(any(DemandeConge.class))).willAnswer(invocation -> {
            DemandeConge saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        DemandeCongeResponse result = service.create(user(),
                new DemandeCongeRequest(1L, lundi, vendredi, "  Vacances  "));

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.statut()).isEqualTo(StatutDemandeConge.EN_ATTENTE);
        assertThat(result.nombreJours()).isEqualByComparingTo("5");
        assertThat(result.typeCongeCode()).isEqualTo("ANNUEL");
        assertThat(result.employeId()).isEqualTo(4L);
        assertThat(result.commentaire()).isEqualTo("Vacances");

        ArgumentCaptor<DemandeConge> captor = ArgumentCaptor.forClass(DemandeConge.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDateCreation()).isEqualTo(LocalDateTime.of(2026, 11, 3, 10, 0));
        assertThat(captor.getValue().getEmploye()).isSameAs(lina);

        // la demande enregistrée est annoncée aux écouteurs (notification du manager)
        verify(eventPublisher).publishEvent(
                new DemandeCongeEvenement(DemandeCongeEvenement.Type.CREEE, captor.getValue()));
    }

    @Test
    @DisplayName("Création : refusée si la date de fin précède la date de début")
    void create_shouldThrowBadRequest_whenEndIsBeforeStart() {
        givenEmployeeAndType(annuel);

        assertThatThrownBy(() -> service.create(user(),
                new DemandeCongeRequest(1L, LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 9), null)))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("postérieure");

        verifyNoInteractions(joursOuvrablesService, soldeService);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Création : refusée si la demande chevauche deux années")
    void create_shouldThrowBadRequest_whenRequestSpansTwoYears() {
        givenEmployeeAndType(annuel);

        assertThatThrownBy(() -> service.create(user(),
                new DemandeCongeRequest(1L, LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 5), null)))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("deux années");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Création : un congé annuel ne peut pas commencer dans le passé")
    void create_shouldThrowBadRequest_whenAnnualLeaveStartsInThePast() {
        givenEmployeeAndType(annuel);

        assertThatThrownBy(() -> service.create(user(),
                new DemandeCongeRequest(1L, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4), null)))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("passé");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Création : un congé maladie peut être déclaré après coup, sans toucher au solde")
    void create_shouldAcceptRetroactiveSickLeave_withoutCheckingBalance() {
        LocalDate lundi = LocalDate.of(2026, 11, 2);
        LocalDate mardi = LocalDate.of(2026, 11, 3);
        givenEmployeeAndType(maladie);
        given(joursOuvrablesService.compter(lundi, mardi)).willReturn(2);
        given(repository.existsByEmployeIdAndStatutInAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                eq(4L), anyCollection(), eq(mardi), eq(lundi))).willReturn(false);
        given(repository.save(any(DemandeConge.class))).willAnswer(invocation -> invocation.getArgument(0));

        DemandeCongeResponse result = service.create(user(), new DemandeCongeRequest(2L, lundi, mardi, null));

        assertThat(result.statut()).isEqualTo(StatutDemandeConge.EN_ATTENTE);
        assertThat(result.nombreJours()).isEqualByComparingTo("2");
        verifyNoInteractions(soldeService);
    }

    @Test
    @DisplayName("Création : refusée pour une année trop lointaine")
    void create_shouldThrowBadRequest_whenYearIsTooFarInTheFuture() {
        givenEmployeeAndType(annuel);

        assertThatThrownBy(() -> service.create(user(),
                new DemandeCongeRequest(1L, LocalDate.of(2028, 1, 10), LocalDate.of(2028, 1, 12), null)))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("ne sont acceptées");
    }

    @Test
    @DisplayName("Création : refusée si la période ne contient aucun jour ouvrable")
    void create_shouldThrowBadRequest_whenThereIsNoWorkingDay() {
        LocalDate samedi = LocalDate.of(2026, 11, 14);
        LocalDate dimanche = LocalDate.of(2026, 11, 15);
        givenEmployeeAndType(annuel);
        given(joursOuvrablesService.compter(samedi, dimanche)).willReturn(0);

        assertThatThrownBy(() -> service.create(user(), new DemandeCongeRequest(1L, samedi, dimanche, null)))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("aucun jour ouvrable");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Création : refusée si elle chevauche une demande en attente ou approuvée")
    void create_shouldThrowConflict_whenRequestOverlapsAnotherOne() {
        LocalDate lundi = LocalDate.of(2026, 11, 9);
        LocalDate vendredi = LocalDate.of(2026, 11, 13);
        givenEmployeeAndType(annuel);
        given(joursOuvrablesService.compter(lundi, vendredi)).willReturn(5);
        given(repository.existsByEmployeIdAndStatutInAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                eq(4L), anyCollection(), eq(vendredi), eq(lundi))).willReturn(true);

        assertThatThrownBy(() -> service.create(user(), new DemandeCongeRequest(1L, lundi, vendredi, null)))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("chevauche");

        verify(repository, never()).save(any());
        verifyNoInteractions(soldeService);
    }

    @Test
    @DisplayName("Création : refusée si le solde est insuffisant")
    void create_shouldThrowConflict_whenBalanceIsInsufficient() {
        LocalDate lundi = LocalDate.of(2026, 11, 9);
        LocalDate vendredi = LocalDate.of(2026, 11, 13);
        givenEmployeeAndType(annuel);
        given(joursOuvrablesService.compter(lundi, vendredi)).willReturn(5);
        given(repository.existsByEmployeIdAndStatutInAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                eq(4L), anyCollection(), eq(vendredi), eq(lundi))).willReturn(false);
        given(soldeService.calculateAvailable(lina, annuel, 2026)).willReturn(new BigDecimal("3.0"));

        assertThatThrownBy(() -> service.create(user(), new DemandeCongeRequest(1L, lundi, vendredi, null)))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("Solde insuffisant")
                .hasMessageContaining("3 jour(s) disponible(s)")
                .hasMessageContaining("5 demandé(s)");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Création : 404 si le type de congé n'existe pas")
    void create_shouldThrowNotFound_whenTypeDoesNotExist() {
        given(employeRepository.findByIdForUpdate(4L)).willReturn(Optional.of(lina));
        given(typeCongeRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(user(), new DemandeCongeRequest(
                99L, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 13), null)))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("Type de congé introuvable");
    }

    // ---------- annulation ----------

    @Test
    @DisplayName("Annulation : une demande en attente devient annulée")
    void cancel_shouldCancelPendingRequest() {
        DemandeConge demande = demande(10L, lina, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));

        DemandeCongeResponse result = service.cancel(user(), 10L);

        assertThat(result.statut()).isEqualTo(StatutDemandeConge.ANNULEE);
        assertThat(demande.getStatut()).isEqualTo(StatutDemandeConge.ANNULEE);
    }

    @Test
    @DisplayName("Annulation : refusée si la demande n'est plus en attente")
    void cancel_shouldThrowConflict_whenRequestIsNotPending() {
        DemandeConge demande = demande(10L, lina, StatutDemandeConge.REFUSEE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.cancel(user(), 10L))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("ne peut plus être annulée");
        assertThat(demande.getStatut()).isEqualTo(StatutDemandeConge.REFUSEE);


    }

    @Test
    @DisplayName("Annulation : la demande d'un autre employé est présentée comme introuvable")
    void cancel_shouldThrowNotFound_whenRequestBelongsToSomeoneElse() {
        DemandeConge demande = demande(10L, employe(3L), StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.cancel(user(), 10L))
                .isInstanceOf(RessourceIntrouvableException.class);

        assertThat(demande.getStatut()).isEqualTo(StatutDemandeConge.EN_ATTENTE);
    }

    // ---------- consultation ----------

    @Test
    @DisplayName("Détail : je consulte ma propre demande")
    void findById_shouldReturnOwnRequest() {
        given(repository.findById(10L)).willReturn(Optional.of(demande(10L, lina, StatutDemandeConge.EN_ATTENTE)));

        DemandeCongeResponse result = service.findById(user(), 10L);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.employeId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("Détail : la demande d'un autre employé est présentée comme introuvable")
    void findById_shouldThrowNotFound_whenRequestBelongsToSomeoneElse() {
        given(repository.findById(10L))
                .willReturn(Optional.of(demande(10L, employe(3L), StatutDemandeConge.EN_ATTENTE)));

        assertThatThrownBy(() -> service.findById(user(), 10L))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    // ---------- méthodes utilitaires ----------

    private void givenEmployeeAndType(TypeConge type) {
        given(employeRepository.findByIdForUpdate(4L)).willReturn(Optional.of(lina));
        given(typeCongeRepository.findById(type.getId())).willReturn(Optional.of(type));
    }

    private UtilisateurConnecte user() {
        return new UtilisateurConnecte(4L, 4L, 1L, "employe", "hash", Roles.EMPLOYE, true);
    }

    private Employe employe(Long id) {
        Employe e = new Employe();
        e.setId(id);
        e.setPrenom("Lina");
        e.setNom("Gharbi");
        return e;
    }

    private TypeConge type(Long id, String code, boolean deduitDuSolde, boolean retroactif) {
        TypeConge t = new TypeConge();
        t.setId(id);
        t.setCode(code);
        t.setLibelle(code);
        t.setDeduitDuSolde(deduitDuSolde);
        t.setRetroactifAutorise(retroactif);
        return t;
    }

    private DemandeConge demande(Long id, Employe employe, StatutDemandeConge statut) {
        DemandeConge d = new DemandeConge();
        d.setId(id);
        d.setEmploye(employe);
        d.setTypeConge(annuel);
        d.setDateDebut(LocalDate.of(2026, 12, 14));
        d.setDateFin(LocalDate.of(2026, 12, 18));
        d.setNombreJours(new BigDecimal("4.0"));
        d.setStatut(statut);
        d.setDateCreation(LocalDateTime.of(2026, 11, 3, 9, 0));
        return d;
    }
}