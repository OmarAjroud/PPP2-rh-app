package com.hrapp.rh.assistant;

import com.hrapp.rh.audit.AuditAction;
import com.hrapp.rh.audit.AuditService;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.conge.DemandeCongeService;
import com.hrapp.rh.conge.SoldeCongeService;
import com.hrapp.rh.conge.StatutDemandeConge;
import com.hrapp.rh.departement.DepartementService;
import com.hrapp.rh.employe.EmployeService;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import com.hrapp.rh.tableaudebord.TableauDeBordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OutilsRhServiceTest {

    @Mock private EmployeService employeService;
    @Mock private DepartementService departementService;
    @Mock private TableauDeBordService tableauDeBordService;
    @Mock private SoldeCongeService soldeService;
    @Mock private DemandeCongeService demandeService;
    @Mock private AuditService auditService;

    private OutilsRhService outils;

    private final UtilisateurConnecte rh = user(2L, 2L, Roles.RH);
    private final UtilisateurConnecte karim = user(3L, 1L, Roles.MANAGER);
    private final UtilisateurConnecte lina = user(4L, 1L, Roles.EMPLOYE);
    private final LocalDate lundi = LocalDate.of(2026, 11, 9);
    private final LocalDate vendredi = LocalDate.of(2026, 11, 13);

    @BeforeEach
    void setUp() {
        outils = new OutilsRhService(employeService, departementService, tableauDeBordService, soldeService,
                demandeService, auditService, JsonMapper.builder().build());
    }

    // ---------- absences : le périmètre dépend du rôle ----------

    @Test
    @DisplayName("Absences : RH voit toute l'entreprise, ou le département demandé")
    void absences_shouldCoverRequestedScope_forHr() {
        outils.execute(rh, "absences_periode", Map.of("du", "2026-11-09", "au", "2026-11-13"), OutilsRhService.Source.CHAT);
        outils.execute(rh, "absences_periode", Map.of("du", "2026-11-09", "au", "2026-11-13", "departementId", 2),
                OutilsRhService.Source.CHAT);

        verify(demandeService).listAll(StatutDemandeConge.APPROUVEE, null, null, lundi, vendredi, 0, 50);
        verify(demandeService).listAll(StatutDemandeConge.APPROUVEE, null, 2L, lundi, vendredi, 0, 50);
    }

    @Test
    @DisplayName("Absences : un manager est limité à son département, même sans le préciser")
    void absences_shouldForceOwnDepartment_forManager() {
        outils.execute(karim, "absences_periode", Map.of("du", "2026-11-09", "au", "2026-11-13"), OutilsRhService.Source.CHAT);

        verify(demandeService).listAll(StatutDemandeConge.APPROUVEE, null, 1L, lundi, vendredi, 0, 50);
    }

    @Test
    @DisplayName("Absences : un manager qui demande un autre département est refusé, sans requête")
    void absences_shouldDenyManager_otherDepartment() {
        ResultatOutil resultat = outils.execute(karim, "absences_periode",
                Map.of("du", "2026-11-09", "au", "2026-11-13", "departementId", 2), OutilsRhService.Source.CHAT);

        assertThat(resultat.erreur()).isTrue();
        assertThat(resultat.contenu()).startsWith("Accès refusé");
        verifyNoInteractions(demandeService);
    }

    @Test
    @DisplayName("Absences : un employé ne voit que ses propres congés")
    void absences_shouldOnlyShowOwnLeave_forEmployee() {
        outils.execute(lina, "absences_periode", Map.of("du", "2026-11-09", "au", "2026-11-13", "departementId", 1),
                OutilsRhService.Source.CHAT);

        verify(demandeService).listAll(StatutDemandeConge.APPROUVEE, 4L, null, lundi, vendredi, 0, 50);
    }

    @Test
    @DisplayName("Absences : période inversée ou trop longue refusée")
    void absences_shouldRejectInvalidPeriod() {
        assertThat(outils.execute(rh, "absences_periode", Map.of("du", "2026-11-13", "au", "2026-11-09"),
                OutilsRhService.Source.CHAT).contenu()).contains("précède");
        assertThat(outils.execute(rh, "absences_periode", Map.of("du", "2026-01-01", "au", "2027-06-01"),
                OutilsRhService.Source.CHAT).contenu()).contains("366");
        verifyNoInteractions(demandeService);
    }

    // ---------- demandes en attente : la « boîte » de chacun ----------

    @Test
    @DisplayName("Demandes en attente : RH toutes, manager son équipe, employé les siennes")
    void pending_shouldUseTheRightInbox_forEachRole() {
        outils.execute(rh, "demandes_en_attente", Map.of(), OutilsRhService.Source.CHAT);
        outils.execute(karim, "demandes_en_attente", Map.of(), OutilsRhService.Source.CHAT);
        outils.execute(lina, "demandes_en_attente", Map.of(), OutilsRhService.Source.CHAT);

        verify(demandeService).listAll(StatutDemandeConge.EN_ATTENTE, null, null, null, null, 0, 20);
        verify(demandeService).listTeam(karim, StatutDemandeConge.EN_ATTENTE, null, 0, 20);
        verify(demandeService).listMine(lina, StatutDemandeConge.EN_ATTENTE, null, 0, 20);
    }

    // ---------- délégation aux services (leurs règles s'appliquent) ----------

    @Test
    @DisplayName("Recherche : déléguée au service avec l'utilisateur, au plus 20 résultats")
    void search_shouldDelegateWithUser_andLimitResults() {
        outils.execute(karim, "rechercher_employes", Map.of("recherche", "  gharbi "), OutilsRhService.Source.CHAT);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(employeService).search(eq(karim), eq("gharbi"), isNull(), isNull(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("Solde : sans employé (ou soi-même) les siens, sinon contrôle d'accès du service")
    void balance_shouldUseOwnOrCheckedEndpoint() {
        outils.execute(lina, "solde_conges", Map.of(), OutilsRhService.Source.CHAT);
        outils.execute(lina, "solde_conges", Map.of("employeId", 4), OutilsRhService.Source.CHAT);
        outils.execute(karim, "solde_conges", Map.of("employeId", 4, "annee", 2026), OutilsRhService.Source.CHAT);

        verify(soldeService, org.mockito.Mockito.times(2)).findMine(lina, null);
        verify(soldeService).findForEmployee(karim, 4L, 2026);
    }

    @Test
    @DisplayName("Arguments : departementId = 0 veut dire « toute l'entreprise » (habitude des modèles de langage)")
    void arguments_shouldTreatZeroIdAsNotSpecified() {
        outils.execute(rh, "statistiques_departement", Map.of("departementId", 0), OutilsRhService.Source.CHAT);
        outils.execute(lina, "solde_conges", Map.of("employeId", 0), OutilsRhService.Source.CHAT);

        verify(tableauDeBordService).build(rh, null, null);
        verify(soldeService).findMine(lina, null);
    }

    @Test
    @DisplayName("Arguments : un nombre envoyé sous forme de texte est accepté")
    void arguments_shouldAcceptNumbersAsText() {
        outils.execute(rh, "consulter_employe", Map.of("employeId", "4"), OutilsRhService.Source.MCP);

        verify(employeService).findById(rh, 4L);
    }

    // ---------- erreurs : renvoyées au modèle, jamais levées ----------

    @Test
    @DisplayName("Refus d'accès d'un service : erreur lisible, journalisée")
    void execute_shouldTurnAccessDeniedIntoToolError() {
        given(employeService.findById(lina, 3L)).willThrow(new AccessDeniedException("Accès refusé à cet employé"));

        ResultatOutil resultat = outils.execute(lina, "consulter_employe", Map.of("employeId", 3), OutilsRhService.Source.MCP);

        assertThat(resultat).isEqualTo(new ResultatOutil(true, "Accès refusé à cet employé"));
        verify(auditService).recordAs(eq(4L), eq("employe"), eq(AuditAction.APPEL_ASSISTANT), eq("AssistantRH"),
                isNull(), contains("\"erreur\":\"Accès refusé à cet employé\""));
    }

    @Test
    @DisplayName("Introuvable, paramètre manquant, date invalide, outil inconnu : erreurs lisibles")
    void execute_shouldReportReadableErrors() {
        given(soldeService.findForEmployee(lina, 3L, null)).willThrow(new RessourceIntrouvableException("Employé introuvable (id=3)"));

        assertThat(outils.execute(lina, "solde_conges", Map.of("employeId", 3), OutilsRhService.Source.CHAT).contenu())
                .isEqualTo("Employé introuvable (id=3)");
        assertThat(outils.execute(rh, "consulter_employe", Map.of(), OutilsRhService.Source.CHAT).contenu())
                .contains("employeId").contains("obligatoire");
        assertThat(outils.execute(rh, "absences_periode", Map.of("du", "demain", "au", "2026-11-13"),
                OutilsRhService.Source.CHAT).contenu()).contains("AAAA-MM-JJ");
        assertThat(outils.execute(rh, "supprimer_employe", Map.of(), OutilsRhService.Source.CHAT).contenu())
                .contains("Outil inconnu");
    }

    @Test
    @DisplayName("Erreur inattendue : message générique (aucun détail technique transmis au modèle)")
    void execute_shouldHideTechnicalDetails() {
        given(departementService.findAll()).willThrow(new IllegalStateException("SQL: connection reset"));

        ResultatOutil resultat = outils.execute(rh, "lister_departements", Map.of(), OutilsRhService.Source.CHAT);

        assertThat(resultat.erreur()).isTrue();
        assertThat(resultat.contenu()).doesNotContain("SQL");
    }

    @Test
    @DisplayName("Audit : chaque appel est journalisé avec l'utilisateur, l'outil, la source et les arguments")
    void execute_shouldAuditEveryCall() {
        given(departementService.findAll()).willReturn(List.of());

        ResultatOutil resultat = outils.execute(karim, "lister_departements", Map.of(), OutilsRhService.Source.MCP);

        assertThat(resultat).isEqualTo(new ResultatOutil(false, "[]"));
        verify(auditService).recordAs(eq(3L), eq("manager"), eq(AuditAction.APPEL_ASSISTANT), eq("AssistantRH"),
                isNull(), eq("{\"outil\":\"lister_departements\",\"source\":\"MCP\",\"arguments\":{}}"));
    }

    @Test
    @DisplayName("Lecture seule : aucun outil ne modifie de données")
    void definitions_shouldOnlyContainReadOnlyTools() {
        assertThat(outils.definitions()).extracting(OutilRh::nom).containsExactly(
                "lister_departements", "rechercher_employes", "consulter_employe", "statistiques_departement",
                "solde_conges", "demandes_en_attente", "absences_periode");
    }

    private static UtilisateurConnecte user(Long employeId, Long departementId, String role) {
        String nom = switch (role) {
            case Roles.RH -> "rh";
            case Roles.MANAGER -> "manager";
            default -> "employe";
        };
        return new UtilisateurConnecte(employeId, employeId, departementId, nom, "hash", role, true);
    }
}
