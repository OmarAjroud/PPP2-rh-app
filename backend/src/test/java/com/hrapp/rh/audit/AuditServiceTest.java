package com.hrapp.rh.audit;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditRepository repository;

    private ObjectMapper mapper;
    private AuditService service;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new AuditService(repository, mapper);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Enregistrement : l'auteur est l'utilisateur connecté")
    void record_shouldUseConnectedUserAsActor() {
        connect(2L, "rh");

        service.record(AuditAction.INVITATION, "Utilisateur", 5L, "Invitation envoyée");

        AuditRecord ligne = captureInserted();
        assertThat(ligne.utilisateurId()).isEqualTo(2L);
        assertThat(ligne.nomUtilisateur()).isEqualTo("rh");
        assertThat(ligne.action()).isEqualTo(AuditAction.INVITATION);
        assertThat(ligne.entite()).isEqualTo("Utilisateur");
        assertThat(ligne.entiteId()).isEqualTo("5");
        assertThat(ligne.details()).isEqualTo("Invitation envoyée");
        assertThat(ligne.dateAction()).isNotNull();
    }

    @Test
    @DisplayName("Enregistrement : sans utilisateur connecté, l'auteur est « systeme »")
    void record_shouldUseSystemActor_whenNobodyIsLoggedIn() {
        service.record(AuditAction.CREATION, "Role", 9L, null);

        AuditRecord ligne = captureInserted();
        assertThat(ligne.utilisateurId()).isNull();
        assertThat(ligne.nomUtilisateur()).isEqualTo("systeme");
    }

    @Test
    @DisplayName("Enregistrement au nom de : l'auteur indiqué prime sur l'utilisateur connecté")
    void recordAs_shouldUseGivenActor_evenWhenAnotherUserIsConnected() {
        connect(2L, "rh");

        service.recordAs(7L, "yassine@rh.com", AuditAction.ACTIVATION_COMPTE, "Utilisateur", 7L, null);

        AuditRecord ligne = captureInserted();
        assertThat(ligne.utilisateurId()).isEqualTo(7L);
        assertThat(ligne.nomUtilisateur()).isEqualTo("yassine@rh.com");
    }

    @Test
    @DisplayName("Changement de données : les valeurs sont écrites en JSON")
    void recordChange_shouldWriteValuesAsJson() throws Exception {
        connect(1L, "admin");
        Map<String, Object> anciennes = new LinkedHashMap<>();
        anciennes.put("salaire", new BigDecimal("2500.00"));
        Map<String, Object> nouvelles = new LinkedHashMap<>();
        nouvelles.put("salaire", new BigDecimal("2600.00"));
        nouvelles.put("dateEmbauche", LocalDate.of(2023, 9, 1));
        nouvelles.put("managerId", null);

        service.recordChange(AuditAction.MODIFICATION, "Employe", 4L, anciennes, nouvelles);

        AuditRecord ligne = captureInserted();
        assertThat(ligne.action()).isEqualTo(AuditAction.MODIFICATION);
        assertThat(ligne.entiteId()).isEqualTo("4");
        JsonNode avant = mapper.readTree(ligne.anciennesValeurs());
        JsonNode apres = mapper.readTree(ligne.nouvellesValeurs());
        assertThat(avant.get("salaire").decimalValue()).isEqualByComparingTo("2500");
        assertThat(apres.get("salaire").decimalValue()).isEqualByComparingTo("2600");
        assertThat(apres.get("dateEmbauche").asText()).isEqualTo("2023-09-01");
        assertThat(apres.get("managerId").isNull()).isTrue();
    }

    @Test
    @DisplayName("Enregistrement : les détails trop longs sont tronqués à 500 caractères")
    void record_shouldTruncateLongDetails() {
        service.record(AuditAction.INVITATION, "Utilisateur", 1L, "x".repeat(600));

        assertThat(captureInserted().details()).hasSize(500);
    }

    @Test
    @DisplayName("Recherche : refusée si la date de début est après la date de fin")
    void search_shouldThrowBadRequest_whenPeriodIsInverted() {
        AuditFilter filtre = new AuditFilter(null, null, null, null,
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1));

        assertThatThrownBy(() -> service.search(filtre, 0, 20))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("date de début");

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Recherche : la taille de page est plafonnée à 100 et la page ne peut pas être négative")
    void search_shouldCapPageSize_andNeverUseNegativePage() {
        Page<AuditEntryResponse> vide = new PageImpl<>(List.of());
        given(repository.search(any(AuditFilter.class), any(Pageable.class))).willReturn(vide);

        service.search(new AuditFilter(null, null, null, null, null, null), -5, 1000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(any(AuditFilter.class), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    // ---------- méthodes utilitaires ----------

    private void connect(Long utilisateurId, String nom) {
        UtilisateurConnecte utilisateur = new UtilisateurConnecte(utilisateurId, utilisateurId, 1L, nom, "hash", Roles.RH, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(utilisateur, null, utilisateur.getAuthorities()));
    }

    private AuditRecord captureInserted() {
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).insert(captor.capture());
        return captor.getValue();
    }
}
