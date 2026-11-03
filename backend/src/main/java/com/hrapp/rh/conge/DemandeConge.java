package com.hrapp.rh.conge;

import com.hrapp.rh.audit.Audited;
import com.hrapp.rh.employe.Employe;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Demande de congé d'un employé. */
@Audited(ignore = "version")
@Entity
@Table(name = "demandes_conge")
@Getter @Setter @NoArgsConstructor
public class DemandeConge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employe_id")
    private Employe employe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_conge_id")
    private TypeConge typeConge;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "nombre_jours", nullable = false, precision = 5, scale = 1)
    private BigDecimal nombreJours;

    @Column(length = 500)
    private String commentaire;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private StatutDemandeConge statut;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    // Renseignés lors de la validation (étape 8b)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decide_par_id")
    private Employe decidePar;

    @Column(name = "date_decision")
    private LocalDateTime dateDecision;

    @Column(name = "motif_decision", length = 500)
    private String motifDecision;

    @Version
    private Long version;
}