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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SoldeCongeMouvementServiceTest {

    @Mock private SoldeCongeRepository soldeRepository;
    @Mock private DemandeCongeRepository demandeRepository;
    @Mock private TypeCongeRepository typeCongeRepository;
    @Mock private MouvementSoldeRepository mouvementRepository;
    @Mock private EmployeRepository employeRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC);

    private final Employe karim = employe(3L, "Karim", "Mansour", null);
    private final Employe sara = employe(2L, "Sara", "Trabelsi", null);
    private final Employe lina = employe(4L, "Lina", "Gharbi", karim);
    private final TypeConge annuel = type(1L, "ANNUEL", true, "22.0");
    private final TypeConge maladie = type(2L, "MALADIE", false, null);

    private SoldeCongeService service;

    @BeforeEach
    void setUp() {
        service = new SoldeCongeService(soldeRepository, demandeRepository, typeCongeRepository,
                mouvementRepository, employeRepository, clock);
    }

    // ---------- déduction ----------

    @Test
    @DisplayName("Déduction : augmente les jours pris et enregistre un mouvement négatif")
    void deductDays_shouldIncreaseTakenDaysAndRecordDeduction() {
        SoldeConge solde = solde(50L, lina, "22.0", "0.0");
        given(soldeRepository.findForUpdate(4L, 1L, 2026)).willReturn(Optional.of(solde));

        service.deductDays(demande(10L, lina, "4.0"), karim);

        assertThat(solde.getJoursPris()).isEqualByComparingTo("4");
        MouvementSolde mouvement = captureSavedMovement();
        assertThat(mouvement.getTypeMouvement()).isEqualTo(TypeMouvement.DEDUCTION);
        assertThat(mouvement.getJours()).isEqualByComparingTo("-4");
        assertThat(mouvement.getCreePar()).isSameAs(karim);
        assertThat(mouvement.getCommentaire()).contains("n°10");
    }

    @Test
    @DisplayName("Déduction : refusée si le solde ne suffit plus au moment de la validation")
    void deductDays_shouldThrowConflict_whenBalanceIsInsufficient() {
        SoldeConge solde = solde(50L, lina, "22.0", "20.0");
        given(soldeRepository.findForUpdate(4L, 1L, 2026)).willReturn(Optional.of(solde));

        assertThatThrownBy(() -> service.deductDays(demande(10L, lina, "4.0"), karim))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("au moment de la validation");

        assertThat(solde.getJoursPris()).isEqualByComparingTo("20");
        verify(mouvementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Déduction : crée le solde (avec son allocation) s'il n'existe pas encore")
    void deductDays_shouldCreateBalanceFirst_whenNoneExists() {
        given(soldeRepository.findForUpdate(4L, 1L, 2026)).willReturn(Optional.empty());
        given(soldeRepository.save(any(SoldeConge.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.deductDays(demande(10L, lina, "4.0"), karim);

        verify(mouvementRepository, times(2)).save(any(MouvementSolde.class));   // allocation + déduction
    }

    // ---------- remboursement ----------

    @Test
    @DisplayName("Remboursement : rend les jours et enregistre un mouvement positif")
    void refundDays_shouldGiveDaysBackAndRecordRefund() {
        SoldeConge solde = solde(50L, lina, "22.0", "10.0");
        given(soldeRepository.findForUpdate(4L, 1L, 2026)).willReturn(Optional.of(solde));

        service.refundDays(demande(10L, lina, "4.0"), lina);

        assertThat(solde.getJoursPris()).isEqualByComparingTo("6");
        MouvementSolde mouvement = captureSavedMovement();
        assertThat(mouvement.getTypeMouvement()).isEqualTo(TypeMouvement.REMBOURSEMENT);
        assertThat(mouvement.getJours()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("Remboursement : les jours pris ne deviennent jamais négatifs")
    void refundDays_shouldNeverMakeTakenDaysNegative() {
        SoldeConge solde = solde(50L, lina, "22.0", "2.0");
        given(soldeRepository.findForUpdate(4L, 1L, 2026)).willReturn(Optional.of(solde));

        service.refundDays(demande(10L, lina, "4.0"), lina);

        assertThat(solde.getJoursPris()).isEqualByComparingTo("0");
    }

    // ---------- ajustement par les RH ----------

    @Test
    @DisplayName("Ajustement : ajoute des jours au solde alloué et enregistre le mouvement avec son commentaire")
    void adjust_shouldIncreaseAllowanceAndRecordAdjustment() {
        SoldeConge solde = solde(50L, lina, "22.0", "3.0");
        given(employeRepository.findById(4L)).willReturn(Optional.of(lina));
        given(typeCongeRepository.findById(1L)).willReturn(Optional.of(annuel));
        given(soldeRepository.findForUpdate(4L, 1L, 2026)).willReturn(Optional.of(solde));
        given(employeRepository.findById(2L)).willReturn(Optional.of(sara));

        MouvementSoldeResponse result = service.adjust(user(2L, Roles.RH), 4L,
                new AjustementSoldeRequest(1L, 2026, new BigDecimal("5.0"), "  Ancienneté  "));

        assertThat(solde.getJoursAlloues()).isEqualByComparingTo("27");
        assertThat(result.typeMouvement()).isEqualTo(TypeMouvement.AJUSTEMENT);
        assertThat(result.jours()).isEqualByComparingTo("5");
        assertThat(result.soldeApres()).isEqualByComparingTo("24");
        assertThat(result.commentaire()).isEqualTo("Ancienneté");
        assertThat(result.creeParNomComplet()).isEqualTo("Sara Trabelsi");
    }

    @Test
    @DisplayName("Ajustement : le solde alloué ne peut pas passer sous les jours déjà pris")
    void adjust_shouldThrowConflict_whenAllowanceWouldFallBelowTakenDays() {
        SoldeConge solde = solde(50L, lina, "22.0", "20.0");
        given(employeRepository.findById(4L)).willReturn(Optional.of(lina));
        given(typeCongeRepository.findById(1L)).willReturn(Optional.of(annuel));
        given(soldeRepository.findForUpdate(4L, 1L, 2026)).willReturn(Optional.of(solde));

        assertThatThrownBy(() -> service.adjust(user(2L, Roles.RH), 4L,
                new AjustementSoldeRequest(1L, 2026, new BigDecimal("-5.0"), "Correction")))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("jours déjà pris");

        assertThat(solde.getJoursAlloues()).isEqualByComparingTo("22");
        verify(mouvementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ajustement : personne ne peut ajuster son propre solde")
    void adjust_shouldThrowAccessDenied_whenAdjustingOwnBalance() {
        assertThatThrownBy(() -> service.adjust(user(2L, Roles.RH), 2L,
                new AjustementSoldeRequest(1L, 2026, new BigDecimal("5.0"), "Pour moi")))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(soldeRepository, employeRepository, mouvementRepository);
    }

    @Test
    @DisplayName("Ajustement : un ajustement nul est refusé")
    void adjust_shouldThrowBadRequest_whenAdjustmentIsZero() {
        assertThatThrownBy(() -> service.adjust(user(2L, Roles.RH), 4L,
                new AjustementSoldeRequest(1L, 2026, BigDecimal.ZERO, "Rien")))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("nul");

        verifyNoInteractions(employeRepository, soldeRepository);
    }

    @Test
    @DisplayName("Ajustement : refusé pour un type de congé sans solde")
    void adjust_shouldThrowBadRequest_whenTypeHasNoBalance() {
        given(employeRepository.findById(4L)).willReturn(Optional.of(lina));
        given(typeCongeRepository.findById(2L)).willReturn(Optional.of(maladie));

        assertThatThrownBy(() -> service.adjust(user(2L, Roles.RH), 4L,
                new AjustementSoldeRequest(2L, 2026, new BigDecimal("2.0"), "Test")))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("pas de solde");

        verifyNoInteractions(soldeRepository);
    }

    // ---------- historique et consultation ----------

    @Test
    @DisplayName("Historique : solde cumulé après chaque mouvement, le plus récent d'abord")
    void historyMine_shouldComputeRunningBalance_newestFirst() {
        SoldeConge solde = solde(50L, lina, "22.0", "4.0");
        given(mouvementRepository.findHistory(4L, 2026)).willReturn(List.of(
                mouvement(1L, solde, TypeMouvement.ALLOCATION, "22.0", LocalDateTime.of(2026, 11, 1, 9, 0)),
                mouvement(2L, solde, TypeMouvement.DEDUCTION, "-4.0", LocalDateTime.of(2026, 11, 2, 9, 0)),
                mouvement(3L, solde, TypeMouvement.AJUSTEMENT, "2.0", LocalDateTime.of(2026, 11, 3, 9, 0))));

        List<MouvementSoldeResponse> result = service.historyMine(user(4L, Roles.EMPLOYE), 2026);

        assertThat(result).extracting(MouvementSoldeResponse::typeMouvement)
                .containsExactly(TypeMouvement.AJUSTEMENT, TypeMouvement.DEDUCTION, TypeMouvement.ALLOCATION);
        assertThat(result).extracting(MouvementSoldeResponse::soldeApres)
                .containsExactly(new BigDecimal("20.0"), new BigDecimal("18.0"), new BigDecimal("22.0"));
    }

    @Test
    @DisplayName("Historique d'un autre employé : un collègue n'y a pas accès (404)")
    void historyForEmployee_shouldThrowNotFound_whenEmployeeIsNotVisible() {
        given(employeRepository.findById(4L)).willReturn(Optional.of(lina));

        assertThatThrownBy(() -> service.historyForEmployee(user(7L, Roles.EMPLOYE), 4L, 2026))
                .isInstanceOf(RessourceIntrouvableException.class);

        verifyNoInteractions(mouvementRepository);
    }

    @Test
    @DisplayName("Soldes d'un employé : le manager d'équipe peut les consulter")
    void findForEmployee_shouldAllowTheTeamManager() {
        given(employeRepository.findById(4L)).willReturn(Optional.of(lina));
        given(typeCongeRepository.findByDeduitDuSoldeTrueOrderByLibelle()).willReturn(List.of(annuel));
        given(soldeRepository.findByEmployeIdAndTypeCongeIdAndAnnee(4L, 1L, 2026))
                .willReturn(Optional.of(solde(50L, lina, "22.0", "3.0")));

        List<SoldeResponse> result = service.findForEmployee(user(3L, Roles.MANAGER), 4L, 2026);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).joursDisponibles()).isEqualByComparingTo("19");
    }

    // ---------- méthodes utilitaires ----------

    private MouvementSolde captureSavedMovement() {
        ArgumentCaptor<MouvementSolde> captor = ArgumentCaptor.forClass(MouvementSolde.class);
        verify(mouvementRepository).save(captor.capture());
        return captor.getValue();
    }

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

    private TypeConge type(Long id, String code, boolean deduitDuSolde, String joursParAn) {
        TypeConge t = new TypeConge();
        t.setId(id);
        t.setCode(code);
        t.setLibelle(code);
        t.setDeduitDuSolde(deduitDuSolde);
        t.setJoursParAn(joursParAn == null ? null : new BigDecimal(joursParAn));
        return t;
    }

    private SoldeConge solde(Long id, Employe employe, String alloues, String pris) {
        SoldeConge s = new SoldeConge();
        s.setId(id);
        s.setEmploye(employe);
        s.setTypeConge(annuel);
        s.setAnnee(2026);
        s.setJoursAlloues(new BigDecimal(alloues));
        s.setJoursPris(new BigDecimal(pris));
        return s;
    }

    private DemandeConge demande(Long id, Employe employe, String jours) {
        DemandeConge d = new DemandeConge();
        d.setId(id);
        d.setEmploye(employe);
        d.setTypeConge(annuel);
        d.setDateDebut(LocalDate.of(2026, 12, 14));
        d.setDateFin(LocalDate.of(2026, 12, 18));
        d.setNombreJours(new BigDecimal(jours));
        return d;
    }

    private MouvementSolde mouvement(Long id, SoldeConge solde, TypeMouvement type, String jours, LocalDateTime date) {
        MouvementSolde m = new MouvementSolde();
        m.setId(id);
        m.setSolde(solde);
        m.setTypeMouvement(type);
        m.setJours(new BigDecimal(jours));
        m.setDateMouvement(date);
        return m;
    }
}