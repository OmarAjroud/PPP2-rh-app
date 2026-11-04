package com.hrapp.rh.conge;

import com.hrapp.rh.employe.Employe;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Une ligne de l'historique d'un solde de congé. Les jours sont signés : + augmente le disponible, - le diminue. */
@Entity
@Table(name = "mouvements_solde")
@Getter @Setter @NoArgsConstructor
public class MouvementSolde {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solde_id")
    private SoldeConge solde;

    /** Demande à l'origine du mouvement (vide pour une allocation ou un ajustement). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_id")
    private DemandeConge demande;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type_mouvement", nullable = false, length = 20)
    private TypeMouvement typeMouvement;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal jours;

    @Column(name = "date_mouvement", nullable = false)
    private LocalDateTime dateMouvement;

    @Column(length = 255)
    private String commentaire;

    /** Qui a provoqué le mouvement (vide pour une allocation automatique). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par_id")
    private Employe creePar;
}