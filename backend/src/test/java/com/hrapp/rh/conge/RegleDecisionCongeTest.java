package com.hrapp.rh.conge;

import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.employe.EmployeRepository;
import com.hrapp.rh.role.Role;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import com.hrapp.rh.utilisateur.Utilisateur;
import com.hrapp.rh.utilisateur.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

/** La règle « manager d'abord, RH en recours », cas par cas. */
@ExtendWith(MockitoExtension.class)
class RegleDecisionCongeTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private EmployeRepository employeRepository;

    // « maintenant » = jeudi 5 novembre 2026 à 10:00 ; délai de recours : 3 jours
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC);
    private RegleDecisionConge regle;

    private final Employe amine = employe(1L, Roles.ADMIN, null);
    private final Employe sara = employe(2L, Roles.RH, null);
    private final Employe karim = employe(3L, Roles.MANAGER, null);
    private final Employe lina = employe(4L, Roles.EMPLOYE, karim);

    private final UtilisateurConnecte ctxSara = user(2L, Roles.RH);
    private final UtilisateurConnecte ctxAmine = user(1L, Roles.ADMIN);
    private final UtilisateurConnecte ctxKarim = user(3L, Roles.MANAGER);
    private final UtilisateurConnecte ctxLina = user(4L, Roles.EMPLOYE);

    @BeforeEach
    void setUp() {
        regle = new RegleDecisionConge(utilisateurRepository, employeRepository, new CongeProperties(null, 3), clock);
        // par défaut, tous les comptes sont actifs
        lenient().when(utilisateurRepository.findByEmployeId(anyLong())).thenAnswer(i -> Optional.of(compte(true)));
    }

    @Test
    @DisplayName("Lina (manager Karim, demande récente) : seul Karim décide ; RH et ADMIN voient mais ne décident pas")
    void teamRequest_shouldBeDecidedByTheManagerOnly() {
        DemandeConge demande = demande(lina, LocalDateTime.of(2026, 11, 4, 9, 0));

        assertThat(regle.canDecide(ctxKarim, demande)).isTrue();
        assertThat(regle.canDecide(ctxSara, demande)).isFalse();
        assertThat(regle.canDecide(ctxAmine, demande)).isFalse();
        assertThat(regle.recours(demande)).isEmpty();
        assertThat(regle.deciders(demande)).containsExactly(karim);
    }

    @Test
    @DisplayName("Au-delà du délai (3 jours sans décision) : les RH peuvent aussi décider ; le manager garde la main")
    void overdueRequest_shouldOpenFallbackToHr() {
        DemandeConge demande = demande(lina, LocalDateTime.of(2026, 11, 2, 10, 0));    // il y a exactement 3 jours

        assertThat(regle.recours(demande)).contains(RegleDecisionConge.Recours.DELAI_DEPASSE);
        assertThat(regle.canDecide(ctxSara, demande)).isTrue();
        assertThat(regle.canDecide(ctxKarim, demande)).isTrue();
        assertThat(regle.escalationDate(demande)).isEqualTo(LocalDateTime.of(2026, 11, 5, 10, 0));
    }

    @Test
    @DisplayName("Sans manager (Karim, Sara, Amine) : recours immédiat vers les RH, sinon les ADMIN")
    void requestWithoutManager_shouldGoToHr() {
        DemandeConge demandeKarim = demande(karim, LocalDateTime.of(2026, 11, 5, 9, 0));
        given(employeRepository.findByRoleNom(Roles.RH)).willReturn(List.of(sara));

        assertThat(regle.recours(demandeKarim)).contains(RegleDecisionConge.Recours.SANS_MANAGER);
        assertThat(regle.canDecide(ctxSara, demandeKarim)).isTrue();
        assertThat(regle.canDecide(ctxAmine, demandeKarim)).isTrue();
        assertThat(regle.deciders(demandeKarim)).containsExactly(sara);
    }

    @Test
    @DisplayName("Demande de la seule RH (Sara) : décidée par l'ADMIN, jamais par elle-même")
    void onlyHrRequest_shouldGoToAdmin() {
        DemandeConge demandeSara = demande(sara, LocalDateTime.of(2026, 11, 5, 9, 0));
        given(employeRepository.findByRoleNom(Roles.RH)).willReturn(List.of(sara));
        given(employeRepository.findByRoleNom(Roles.ADMIN)).willReturn(List.of(amine));

        assertThat(regle.canDecide(ctxSara, demandeSara)).isFalse();
        assertThat(regle.canDecide(ctxAmine, demandeSara)).isTrue();
        assertThat(regle.deciders(demandeSara)).containsExactly(amine);
    }

    @Test
    @DisplayName("Manager au compte désactivé : recours immédiat aux RH, et il ne peut plus décider")
    void deactivatedManager_shouldOpenFallback() {
        DemandeConge demande = demande(lina, LocalDateTime.of(2026, 11, 5, 9, 0));
        given(utilisateurRepository.findByEmployeId(3L)).willReturn(Optional.of(compte(false)));
        given(employeRepository.findByRoleNom(Roles.RH)).willReturn(List.of(sara));

        assertThat(regle.recours(demande)).contains(RegleDecisionConge.Recours.MANAGER_INDISPONIBLE);
        assertThat(regle.canDecide(ctxKarim, demande)).isFalse();
        assertThat(regle.canDecide(ctxSara, demande)).isTrue();
        assertThat(regle.deciders(demande)).containsExactly(sara);
    }

    @Test
    @DisplayName("Manager qui n'a pas de rôle de décideur (ex. un EMPLOYE désigné comme manager) : recours aux RH")
    void managerWithoutDecisionRole_shouldOpenFallback() {
        Employe collegue = employe(5L, Roles.EMPLOYE, null);
        Employe stagiaire = employe(6L, Roles.EMPLOYE, collegue);
        DemandeConge demande = demande(stagiaire, LocalDateTime.of(2026, 11, 5, 9, 0));

        assertThat(regle.activeManager(demande)).isEmpty();
        assertThat(regle.recours(demande)).contains(RegleDecisionConge.Recours.MANAGER_INDISPONIBLE);
        assertThat(regle.canDecide(user(5L, Roles.EMPLOYE), demande)).isFalse();
    }

    @Test
    @DisplayName("Un RH qui est le manager direct décide comme manager, sans attendre le délai")
    void hrWhoIsTheDirectManager_shouldDecideAsManager() {
        Employe assistantRh = employe(7L, Roles.EMPLOYE, sara);
        DemandeConge demande = demande(assistantRh, LocalDateTime.of(2026, 11, 5, 9, 0));

        assertThat(regle.recours(demande)).isEmpty();
        assertThat(regle.canDecide(ctxSara, demande)).isTrue();
        assertThat(regle.canDecide(ctxAmine, demande)).isFalse();
    }

    @Test
    @DisplayName("Personne ne décide de sa propre demande, même en recours")
    void nobody_shouldDecideOwnRequest() {
        DemandeConge demande = demande(lina, LocalDateTime.of(2026, 10, 1, 9, 0));     // très en retard

        assertThat(regle.canDecide(ctxLina, demande)).isFalse();
    }

    @Test
    @DisplayName("Décideurs de recours : les comptes inactifs ou non activés sont ignorés")
    void fallbackDeciders_shouldSkipInactiveAccounts() {
        Employe rhInactive = employe(8L, Roles.RH, null);
        DemandeConge demande = demande(karim, LocalDateTime.of(2026, 11, 5, 9, 0));
        given(employeRepository.findByRoleNom(Roles.RH)).willReturn(List.of(rhInactive, sara));
        given(utilisateurRepository.findByEmployeId(8L)).willReturn(Optional.of(compte(false)));

        assertThat(regle.fallbackDeciders(demande)).containsExactly(sara);
    }

    // ---------- méthodes utilitaires ----------

    private static Employe employe(Long id, String role, Employe manager) {
        Role r = new Role();
        r.setNom(role);
        Employe e = new Employe();
        e.setId(id);
        e.setPrenom("P" + id);
        e.setNom("N" + id);
        e.setRole(r);
        e.setManager(manager);
        return e;
    }

    private static Utilisateur compte(boolean actif) {
        Utilisateur u = new Utilisateur();
        u.setActif(actif);
        u.setMotDePasseHash("hash");
        return u;
    }

    private static DemandeConge demande(Employe employe, LocalDateTime creation) {
        DemandeConge d = new DemandeConge();
        d.setId(10L);
        d.setEmploye(employe);
        d.setStatut(StatutDemandeConge.EN_ATTENTE);
        d.setDateCreation(creation);
        return d;
    }

    private static UtilisateurConnecte user(Long employeId, String role) {
        return new UtilisateurConnecte(employeId, employeId, 1L, "u" + employeId, "hash", role, true);
    }
}
