package com.hrapp.rh.securite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Jeton de rafraîchissement déjà utilisé ou révoqué : il ne sera plus jamais accepté. */
@Entity
@Table(name = "jetons_revoques")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JetonRevoque {

    @Id
    @Column(length = 36)
    private String jti;

    @Column(name = "utilisateur_id", nullable = false)
    private Long utilisateurId;

    @Column(name = "date_expiration", nullable = false)
    private LocalDateTime dateExpiration;
}
