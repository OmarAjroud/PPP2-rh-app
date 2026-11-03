package com.hrapp.rh.conge;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Type de congé (annuel, maladie, sans solde...). */
@Entity
@Table(name = "types_conge")
@Getter @Setter @NoArgsConstructor
public class TypeConge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String libelle;

    /** Ce type consomme-t-il un solde annuel ? */
    @Column(name = "deduit_du_solde", nullable = false)
    private boolean deduitDuSolde;

    /** Jours alloués chaque année (vide si le type n'a pas de solde). */
    @Column(name = "jours_par_an", precision = 5, scale = 1)
    private BigDecimal joursParAn;

    /** Peut-on déclarer ce congé après coup (ex. arrêt maladie) ? */
    @Column(name = "retroactif_autorise", nullable = false)
    private boolean retroactifAutorise;
}