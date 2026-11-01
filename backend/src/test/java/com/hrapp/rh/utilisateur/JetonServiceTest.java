package com.hrapp.rh.utilisateur;

import com.hrapp.rh.common.exception.RequeteInvalideException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JetonServiceTest {

    @Mock private JetonUtilisateurRepository repository;

    private JetonService service;

    @BeforeEach
    void setUp() {
        service = new JetonService(repository, 48, 60);
    }

    // ---------- émission ----------

    @Test
    @DisplayName("Émission : seule l'empreinte SHA-256 est stockée, jamais le jeton en clair")
    void issue_shouldStoreOnlyTheHash_andReturnTheRawToken() throws Exception {
        Utilisateur utilisateur = utilisateur(7L);

        String jeton = service.issue(utilisateur, TypeJeton.ACTIVATION);

        ArgumentCaptor<JetonUtilisateur> captor = ArgumentCaptor.forClass(JetonUtilisateur.class);
        verify(repository).save(captor.capture());
        JetonUtilisateur saved = captor.getValue();

        assertThat(jeton).hasSize(43);                                    // 32 octets en base64url
        assertThat(saved.getJetonHash()).isEqualTo(sha256(jeton)).isNotEqualTo(jeton);
        assertThat(saved.getTypeJeton()).isEqualTo(TypeJeton.ACTIVATION);
        assertThat(saved.getUtilisateur()).isSameAs(utilisateur);
        assertThat(saved.getDateUtilisation()).isNull();
        assertThat(saved.getDateExpiration())
                .isBetween(LocalDateTime.now().plusHours(47), LocalDateTime.now().plusHours(49));
        verify(repository).invalidateUnused(eq(7L), eq(TypeJeton.ACTIVATION), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Émission : deux jetons successifs sont différents")
    void issue_shouldGenerateDifferentTokensEachTime() {
        String premier = service.issue(utilisateur(7L), TypeJeton.REINITIALISATION);
        String second = service.issue(utilisateur(7L), TypeJeton.REINITIALISATION);

        assertThat(premier).isNotEqualTo(second);
    }

    // ---------- consommation ----------

    @Test
    @DisplayName("Consommation : un jeton valide renvoie l'utilisateur et est marqué utilisé")
    void consume_shouldReturnUser_whenTokenIsValid() {
        JetonUtilisateur jeton = jeton(TypeJeton.ACTIVATION, LocalDateTime.now().plusHours(1), null);
        given(repository.findByJetonHash(anyString())).willReturn(Optional.of(jeton));
        given(repository.markUsed(eq(5L), any(LocalDateTime.class))).willReturn(1);

        Utilisateur result = service.consume("RAW", TypeJeton.ACTIVATION);

        assertThat(result).isSameAs(jeton.getUtilisateur());
    }

    @Test
    @DisplayName("Consommation : refusée si le jeton est inconnu")
    void consume_shouldThrow_whenTokenIsUnknown() {
        given(repository.findByJetonHash(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.consume("RAW", TypeJeton.ACTIVATION))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("invalide ou a expiré");

        verify(repository, never()).markUsed(any(), any());
    }

    @Test
    @DisplayName("Consommation : refusée si le jeton a expiré")
    void consume_shouldThrow_whenTokenIsExpired() {
        JetonUtilisateur jeton = jeton(TypeJeton.ACTIVATION, LocalDateTime.now().minusMinutes(1), null);
        given(repository.findByJetonHash(anyString())).willReturn(Optional.of(jeton));

        assertThatThrownBy(() -> service.consume("RAW", TypeJeton.ACTIVATION))
                .isInstanceOf(RequeteInvalideException.class);

        verify(repository, never()).markUsed(any(), any());
    }

    @Test
    @DisplayName("Consommation : refusée si le jeton a déjà été utilisé")
    void consume_shouldThrow_whenTokenIsAlreadyUsed() {
        JetonUtilisateur jeton = jeton(TypeJeton.ACTIVATION, LocalDateTime.now().plusHours(1),
                LocalDateTime.now().minusMinutes(5));
        given(repository.findByJetonHash(anyString())).willReturn(Optional.of(jeton));

        assertThatThrownBy(() -> service.consume("RAW", TypeJeton.ACTIVATION))
                .isInstanceOf(RequeteInvalideException.class);

        verify(repository, never()).markUsed(any(), any());
    }

    @Test
    @DisplayName("Consommation : un jeton d'activation ne peut pas servir à réinitialiser un mot de passe")
    void consume_shouldThrow_whenTokenTypeDoesNotMatch() {
        JetonUtilisateur jeton = jeton(TypeJeton.ACTIVATION, LocalDateTime.now().plusHours(1), null);
        given(repository.findByJetonHash(anyString())).willReturn(Optional.of(jeton));

        assertThatThrownBy(() -> service.consume("RAW", TypeJeton.REINITIALISATION))
                .isInstanceOf(RequeteInvalideException.class);

        verify(repository, never()).markUsed(any(), any());
    }

    @Test
    @DisplayName("Consommation : refusée si le jeton vient d'être utilisé par une autre requête")
    void consume_shouldThrow_whenTokenIsConsumedConcurrently() {
        JetonUtilisateur jeton = jeton(TypeJeton.ACTIVATION, LocalDateTime.now().plusHours(1), null);
        given(repository.findByJetonHash(anyString())).willReturn(Optional.of(jeton));
        given(repository.markUsed(eq(5L), any(LocalDateTime.class))).willReturn(0);   // perdu la course

        assertThatThrownBy(() -> service.consume("RAW", TypeJeton.ACTIVATION))
                .isInstanceOf(RequeteInvalideException.class);
    }

    // ---------- méthodes utilitaires ----------

    private Utilisateur utilisateur(Long id) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        return u;
    }

    private JetonUtilisateur jeton(TypeJeton type, LocalDateTime expiration, LocalDateTime utilisation) {
        JetonUtilisateur j = new JetonUtilisateur();
        j.setId(5L);
        j.setUtilisateur(utilisateur(7L));
        j.setTypeJeton(type);
        j.setJetonHash("hash");
        j.setDateCreation(LocalDateTime.now().minusMinutes(10));
        j.setDateExpiration(expiration);
        j.setDateUtilisation(utilisation);
        return j;
    }

    private String sha256(String texte) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(texte.getBytes(StandardCharsets.UTF_8)));
    }
}