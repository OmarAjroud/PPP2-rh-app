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
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DemandeCongeDecisionServiceTest {

    @Mock private DemandeCongeRepository repository;
    @Mock private EmployeRepository employeRepository;
    @Mock private TypeCongeRepository typeCongeRepository;
    @Mock private SoldeCongeService soldeService;
    @Mock private JoursOuvrablesService joursOuvrablesService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RegleDecisionConge regle;
    @Spy  private DemandeCongeMapper mapper = new DemandeCongeMapperImpl();

    // « Aujourd'hui » = mardi 3 novembre 2026
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC);

    private final Employe amine = employe(1L, "Amine", "Ben Salah", null);
    private final Employe sara = employe(2L, "Sara", "Trabelsi", null);
    private final Employe karim = employe(3L, "Karim", "Mansour", null);
    private final Employe lina = employe(4L, "Lina", "Gharbi", karim);       // l'équipe de Karim
    private final TypeConge annuel = type(1L, "ANNUEL", true);
    private final TypeConge maladie = type(2L, "MALADIE", false);

    private DemandeCongeService service;

    @BeforeEach
    void setUp() {
        service = new DemandeCongeService(repository, employeRepository, typeCongeRepository,
                soldeService, joursOuvrablesService, mapper, clock, eventPublisher, regle);
        // par défaut l'acteur est le bon décideur ; la règle elle-même est testée dans RegleDecisionCongeTest
        lenient().when(regle.canDecide(any(), any())).thenReturn(true);
    }

    // ---------- validation ----------

    @Test
    @DisplayName("Validation : le manager de l'équipe approuve, les jours sont déduits et la décision est enregistrée")
    void approve_shouldDeductDaysAndApprove_whenTeamManagerApprovesAnnualLeave() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));
        given(employeRepository.findById(3L)).willReturn(Optional.of(karim));

        DemandeCongeResponse result = service.approve(user(3L, Roles.MANAGER), 10L, "  Bon repos  ");

        verify(soldeService).deductDays(demande, karim);
        assertThat(result.statut()).isEqualTo(StatutDemandeConge.APPROUVEE);
        assertThat(result.decideParNomComplet()).isEqualTo("Karim Mansour");
        assertThat(result.motifDecision()).isEqualTo("Bon repos");
        assertThat(demande.getDecidePar()).isSameAs(karim);
        assertThat(demande.getDateDecision()).isEqualTo(LocalDateTime.of(2026, 11, 3, 10, 0));
    }

    @Test
    @DisplayName("Validation : un type de congé sans solde n'entraîne aucune déduction")
    void approve_shouldNotTouchBalance_whenLeaveTypeHasNoBalance() {
        DemandeConge demande = demande(10L, lina, maladie, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));
        given(employeRepository.findById(3L)).willReturn(Optional.of(karim));

        DemandeCongeResponse result = service.approve(user(3L, Roles.MANAGER), 10L, null);

        assertThat(result.statut()).isEqualTo(StatutDemandeConge.APPROUVEE);
        verifyNoInteractions(soldeService);
    }

    @Test
    @DisplayName("Validation : le manager d'une autre équipe ne voit pas la demande (404)")
    void approve_shouldThrowNotFound_whenManagerIsNotTheEmployeesManager() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.approve(user(9L, Roles.MANAGER), 10L, null))
                .isInstanceOf(RessourceIntrouvableException.class);

        assertThat(demande.getStatut()).isEqualTo(StatutDemandeConge.EN_ATTENTE);
        verifyNoInteractions(soldeService);
    }

    @Test
    @DisplayName("Validation : un administrateur ne peut pas approuver sa propre demande")
    void approve_shouldThrowAccessDenied_whenAdminApprovesOwnRequest() {
        DemandeConge demande = demande(11L, amine, annuel, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(11L)).willReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.approve(user(1L, Roles.ADMIN), 11L, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("propre demande");

        assertThat(demande.getStatut()).isEqualTo(StatutDemandeConge.EN_ATTENTE);
        verifyNoInteractions(soldeService);
    }

    @Test
    @DisplayName("Validation : refusée si la demande a déjà été traitée")
    void approve_shouldThrowConflict_whenRequestIsAlreadyProcessed() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.REFUSEE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.approve(user(3L, Roles.MANAGER), 10L, null))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("déjà été traitée");

        verifyNoInteractions(soldeService);
    }

    @Test
    @DisplayName("Validation : le RH approuve une demande en recours (ex. sans décision du manager dans le délai)")
    void approve_shouldAllowRh_whenRequestIsInFallback() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));
        given(employeRepository.findById(2L)).willReturn(Optional.of(sara));

        DemandeCongeResponse result = service.approve(user(2L, Roles.RH), 10L, null);

        assertThat(result.statut()).isEqualTo(StatutDemandeConge.APPROUVEE);
        assertThat(result.decideParNomComplet()).isEqualTo("Sara Trabelsi");
        verify(soldeService).deductDays(demande, sara);
    }

    @Test
    @DisplayName("Validation : tant que c'est au manager, le RH est refusé (403) avec le nom du manager et la date de recours")
    void approve_shouldDenyRh_whileItIsTheManagersTurn() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));
        given(regle.canDecide(any(), eq(demande))).willReturn(false);
        given(regle.activeManager(demande)).willReturn(Optional.of(karim));
        given(regle.escalationDate(demande)).willReturn(LocalDateTime.of(2026, 11, 6, 9, 0));

        assertThatThrownBy(() -> service.approve(user(2L, Roles.RH), 10L, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Karim Mansour")
                .hasMessageContaining("06/11/2026 à 09:00");
        verifyNoInteractions(soldeService);
        assertThat(demande.getStatut()).isEqualTo(StatutDemandeConge.EN_ATTENTE);
    }

    @Test
    @DisplayName("Refus : même règle, le RH ne refuse pas à la place du manager")
    void reject_shouldDenyRh_whileItIsTheManagersTurn() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));
        given(regle.canDecide(any(), eq(demande))).willReturn(false);
        given(regle.escalationDate(demande)).willReturn(LocalDateTime.of(2026, 11, 6, 9, 0));

        assertThatThrownBy(() -> service.reject(user(2L, Roles.RH), 10L, "Non"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(demande.getStatut()).isEqualTo(StatutDemandeConge.EN_ATTENTE);
    }

    // ---------- interruption par les RH ----------

    @Test
    @DisplayName("Interruption : congé pas encore commencé → annulé, tous les jours rendus")
    void interrupt_shouldCancelAndRefundAll_whenLeaveHasNotStarted() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.APPROUVEE);     // 14-18/12, 4 jours
        given(repository.findById(10L)).willReturn(Optional.of(demande));
        given(employeRepository.findById(2L)).willReturn(Optional.of(sara));

        DemandeCongeResponse result = service.interrupt(user(2L, Roles.RH), 10L, "  Besoin urgent  ");

        assertThat(result.statut()).isEqualTo(StatutDemandeConge.ANNULEE);
        assertThat(result.motifInterruption()).isEqualTo("Besoin urgent");
        verify(soldeService).refundDays(eq(demande), eq(new BigDecimal("4.0")), eq(sara), contains("Besoin urgent"));
        verify(eventPublisher).publishEvent(new DemandeCongeEvenement(DemandeCongeEvenement.Type.INTERROMPUE, demande));
    }

    @Test
    @DisplayName("Interruption : congé en cours → se termine la veille, seuls les jours non pris sont rendus")
    void interrupt_shouldShortenLeave_whenLeaveIsInProgress() {
        // aujourd'hui mardi 3/11 ; congé du lundi 2/11 au vendredi 6/11 (5 jours) : seul le lundi a été pris
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.APPROUVEE);
        demande.setDateDebut(LocalDate.of(2026, 11, 2));
        demande.setDateFin(LocalDate.of(2026, 11, 6));
        demande.setNombreJours(new BigDecimal("5.0"));
        given(repository.findById(10L)).willReturn(Optional.of(demande));
        given(employeRepository.findById(2L)).willReturn(Optional.of(sara));
        given(joursOuvrablesService.compter(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2))).willReturn(1);

        DemandeCongeResponse result = service.interrupt(user(2L, Roles.RH), 10L, "Retour anticipé");

        assertThat(result.statut()).isEqualTo(StatutDemandeConge.APPROUVEE);
        assertThat(result.dateFin()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(result.nombreJours()).isEqualByComparingTo("1");
        verify(soldeService).refundDays(eq(demande), eq(new BigDecimal("4.0")), eq(sara), anyString());
    }

    @Test
    @DisplayName("Interruption : commencé le week-end, aucun jour ouvrable pris → annulé (jamais 0 jour)")
    void interrupt_shouldCancel_whenOnlyWeekendDaysWereTaken() {
        // aujourd'hui mardi 3/11 ; congé commencé le dimanche 1/11 ; le lundi 2/11 était férié : 0 jour ouvrable pris
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.APPROUVEE);
        demande.setDateDebut(LocalDate.of(2026, 11, 1));
        demande.setDateFin(LocalDate.of(2026, 11, 6));
        given(repository.findById(10L)).willReturn(Optional.of(demande));
        given(employeRepository.findById(2L)).willReturn(Optional.of(sara));
        given(joursOuvrablesService.compter(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2))).willReturn(0);

        assertThat(service.interrupt(user(2L, Roles.RH), 10L, "Retour").statut()).isEqualTo(StatutDemandeConge.ANNULEE);
    }

    @Test
    @DisplayName("Interruption : réservée aux RH/ADMIN, motif obligatoire, jamais son propre congé, ni un congé terminé")
    void interrupt_shouldEnforceItsRules() {
        DemandeConge approuvee = demande(10L, lina, annuel, StatutDemandeConge.APPROUVEE);
        DemandeConge terminee = demande(11L, lina, annuel, StatutDemandeConge.APPROUVEE);
        terminee.setDateDebut(LocalDate.of(2026, 10, 1));
        terminee.setDateFin(LocalDate.of(2026, 10, 2));
        DemandeConge propre = demande(12L, sara, annuel, StatutDemandeConge.APPROUVEE);
        given(repository.findById(10L)).willReturn(Optional.of(approuvee));
        given(repository.findById(11L)).willReturn(Optional.of(terminee));
        given(repository.findById(12L)).willReturn(Optional.of(propre));

        assertThatThrownBy(() -> service.interrupt(user(2L, Roles.RH), 10L, " "))
                .isInstanceOf(RequeteInvalideException.class);
        assertThatThrownBy(() -> service.interrupt(user(3L, Roles.MANAGER), 10L, "Motif"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.interrupt(user(2L, Roles.RH), 11L, "Motif"))
                .isInstanceOf(ConflitException.class).hasMessageContaining("terminé");
        assertThatThrownBy(() -> service.interrupt(user(2L, Roles.RH), 12L, "Motif"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(soldeService);
    }

    @Test
    @DisplayName("Validation : un simple employé ne voit pas la demande d'un collègue (404)")
    void approve_shouldThrowNotFound_whenUserIsOnlyAnEmployee() {
        given(repository.findById(10L))
                .willReturn(Optional.of(demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE)));

        assertThatThrownBy(() -> service.approve(user(7L, Roles.EMPLOYE), 10L, null))
                .isInstanceOf(RessourceIntrouvableException.class);

        verifyNoInteractions(soldeService);
    }

    @Test
    @DisplayName("Validation : si le solde est insuffisant au moment de la décision, la demande reste en attente")
    void approve_shouldKeepRequestPending_whenBalanceIsInsufficientAtDecisionTime() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));
        given(employeRepository.findById(3L)).willReturn(Optional.of(karim));
        doThrow(new ConflitException("Solde insuffisant au moment de la validation"))
                .when(soldeService).deductDays(demande, karim);

        assertThatThrownBy(() -> service.approve(user(3L, Roles.MANAGER), 10L, null))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("Solde insuffisant");

        assertThat(demande.getStatut()).isEqualTo(StatutDemandeConge.EN_ATTENTE);
        assertThat(demande.getDecidePar()).isNull();
    }

    // ---------- refus ----------

    @Test
    @DisplayName("Refus : enregistre le motif et le décideur, sans toucher au solde")
    void reject_shouldRejectWithReason_withoutTouchingBalance() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));
        given(employeRepository.findById(3L)).willReturn(Optional.of(karim));

        DemandeCongeResponse result = service.reject(user(3L, Roles.MANAGER), 10L, "Période de clôture");

        assertThat(result.statut()).isEqualTo(StatutDemandeConge.REFUSEE);
        assertThat(result.motifDecision()).isEqualTo("Période de clôture");
        assertThat(demande.getDecidePar()).isSameAs(karim);
        verifyNoInteractions(soldeService);
    }

    @Test
    @DisplayName("Refus : le motif est obligatoire")
    void reject_shouldThrowBadRequest_whenReasonIsBlank() {
        assertThatThrownBy(() -> service.reject(user(3L, Roles.MANAGER), 10L, "   "))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("motif");

        verifyNoInteractions(repository, soldeService);
    }

    @Test
    @DisplayName("Refus : le RH ne peut pas refuser sa propre demande")
    void reject_shouldThrowAccessDenied_whenRhRejectsOwnRequest() {
        DemandeConge demande = demande(12L, sara, annuel, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(12L)).willReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.reject(user(2L, Roles.RH), 12L, "Non"))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(demande.getStatut()).isEqualTo(StatutDemandeConge.EN_ATTENTE);
    }

    @Test
    @DisplayName("Refus : impossible si la demande est déjà approuvée")
    void reject_shouldThrowConflict_whenRequestIsAlreadyApproved() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.APPROUVEE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.reject(user(3L, Roles.MANAGER), 10L, "Trop tard"))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("déjà été traitée");
    }

    // ---------- annulation d'un congé approuvé ----------

    @Test
    @DisplayName("Annulation : un congé approuvé qui n'a pas commencé est annulé et les jours sont rendus")
    void cancel_shouldRefundDays_whenApprovedLeaveStartsInTheFuture() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.APPROUVEE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));

        DemandeCongeResponse result = service.cancel(user(4L, Roles.EMPLOYE), 10L);

        assertThat(result.statut()).isEqualTo(StatutDemandeConge.ANNULEE);
        verify(soldeService).refundDays(demande, lina);
    }

    @Test
    @DisplayName("Annulation : un congé approuvé déjà commencé ne peut plus être annulé")
    void cancel_shouldThrowConflict_whenApprovedLeaveHasAlreadyStarted() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.APPROUVEE);
        demande.setDateDebut(LocalDate.of(2026, 11, 3));          // aujourd'hui
        given(repository.findById(10L)).willReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.cancel(user(4L, Roles.EMPLOYE), 10L))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("déjà commencé");

        assertThat(demande.getStatut()).isEqualTo(StatutDemandeConge.APPROUVEE);
        verifyNoInteractions(soldeService);
    }

    @Test
    @DisplayName("Annulation : un congé approuvé sans solde (maladie) est annulé sans remboursement")
    void cancel_shouldNotRefund_whenApprovedLeaveHasNoBalance() {
        DemandeConge demande = demande(10L, lina, maladie, StatutDemandeConge.APPROUVEE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));

        DemandeCongeResponse result = service.cancel(user(4L, Roles.EMPLOYE), 10L);

        assertThat(result.statut()).isEqualTo(StatutDemandeConge.ANNULEE);
        verifyNoInteractions(soldeService);
    }

    // ---------- consultation ----------

    @Test
    @DisplayName("Détail : le manager d'équipe consulte la demande de son équipe")
    void findById_shouldAllowTheTeamManager() {
        given(repository.findById(10L))
                .willReturn(Optional.of(demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE)));

        assertThat(service.findById(user(3L, Roles.MANAGER), 10L).id()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Détail : le RH consulte n'importe quelle demande")
    void findById_shouldAllowHr() {
        given(repository.findById(10L))
                .willReturn(Optional.of(demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE)));

        assertThat(service.findById(user(2L, Roles.RH), 10L).employeNomComplet()).isEqualTo("Lina Gharbi");
    }

    @Test
    @DisplayName("Détail : le manager d'une autre équipe ne voit pas la demande (404)")
    void findById_shouldHideRequest_fromManagerOfAnotherTeam() {
        given(repository.findById(10L))
                .willReturn(Optional.of(demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE)));

        assertThatThrownBy(() -> service.findById(user(9L, Roles.MANAGER), 10L))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    @DisplayName("Vue globale : refusée si la date de début est après la date de fin")
    void listAll_shouldThrowBadRequest_whenPeriodIsInverted() {
        assertThatThrownBy(() -> service.listAll(null, null, null,
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 1), 0, 10))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("date de début");

        verifyNoInteractions(repository);
    }

    // ---------- événements (notifications) ----------

    @Test
    @DisplayName("Événement : une approbation publie APPROUVEE avec la demande déjà approuvée")
    void approve_shouldPublishApprovedEvent() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));
        given(employeRepository.findById(3L)).willReturn(Optional.of(karim));

        service.approve(user(3L, Roles.MANAGER), 10L, null);

        verify(eventPublisher).publishEvent(new DemandeCongeEvenement(DemandeCongeEvenement.Type.APPROUVEE, demande));
        assertThat(demande.getStatut()).isEqualTo(StatutDemandeConge.APPROUVEE);
    }

    @Test
    @DisplayName("Événement : un refus publie REFUSEE")
    void reject_shouldPublishRejectedEvent() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));
        given(employeRepository.findById(3L)).willReturn(Optional.of(karim));

        service.reject(user(3L, Roles.MANAGER), 10L, "Période chargée");

        verify(eventPublisher).publishEvent(new DemandeCongeEvenement(DemandeCongeEvenement.Type.REFUSEE, demande));
    }

    @Test
    @DisplayName("Événement : aucune publication si la décision échoue (solde insuffisant)")
    void approve_shouldNotPublish_whenDecisionFails() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));
        given(employeRepository.findById(3L)).willReturn(Optional.of(karim));
        doThrow(new ConflitException("Solde insuffisant")).when(soldeService).deductDays(demande, karim);

        assertThatThrownBy(() -> service.approve(user(3L, Roles.MANAGER), 10L, null))
                .isInstanceOf(ConflitException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Événement : une annulation publie ANNULEE")
    void cancel_shouldPublishCancelledEvent() {
        DemandeConge demande = demande(10L, lina, annuel, StatutDemandeConge.EN_ATTENTE);
        given(repository.findById(10L)).willReturn(Optional.of(demande));

        service.cancel(user(4L, Roles.EMPLOYE), 10L);

        verify(eventPublisher).publishEvent(new DemandeCongeEvenement(DemandeCongeEvenement.Type.ANNULEE, demande));
    }

    // ---------- méthodes utilitaires ----------

    private UtilisateurConnecte user(Long employeId, String role) {
        return new UtilisateurConnecte(employeId, employeId, 1L, "test", "hash", role, true);
    }

    private Employe employe(Long id, String prenom, String nom, Employe manager) {
        Employe e = new Employe();
        e.setId(id);
        e.setPrenom(prenom);
        e.setNom(nom);
        e.setManager(manager);
        return e;
    }

    private TypeConge type(Long id, String code, boolean deduitDuSolde) {
        TypeConge t = new TypeConge();
        t.setId(id);
        t.setCode(code);
        t.setLibelle(code);
        t.setDeduitDuSolde(deduitDuSolde);
        return t;
    }

    private DemandeConge demande(Long id, Employe employe, TypeConge type, StatutDemandeConge statut) {
        DemandeConge d = new DemandeConge();
        d.setId(id);
        d.setEmploye(employe);
        d.setTypeConge(type);
        d.setDateDebut(LocalDate.of(2026, 12, 14));
        d.setDateFin(LocalDate.of(2026, 12, 18));
        d.setNombreJours(new BigDecimal("4.0"));
        d.setStatut(statut);
        d.setDateCreation(LocalDateTime.of(2026, 11, 3, 9, 0));
        return d;
    }
}