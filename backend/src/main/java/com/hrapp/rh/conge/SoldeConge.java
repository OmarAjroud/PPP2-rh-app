package com.hrapp.rh.conge;

import com.hrapp.rh.audit.Audited;
import com.hrapp.rh.employe.Employe;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Solde de congé d'un employé pour un type et une année. */
@Audited
@Entity
@Table(name = "soldes_conge")
@Getter @Setter @NoArgsConstructor
public class SoldeConge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employe_id")
    private Employe employe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_conge_id")
    private TypeConge typeConge;

    @Column(nullable = false)
    private int annee;

    @Column(name = "jours_alloues", nullable = false, precision = 5, scale = 1)
    private BigDecimal joursAlloues;

    @Column(name = "jours_pris", nullable = false, precision = 5, scale = 1)
    private BigDecimal joursPris = BigDecimal.ZERO;
}