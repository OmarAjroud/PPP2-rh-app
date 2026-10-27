package com.hrapp.rh.employe;

import com.hrapp.rh.departement.Departement;
import com.hrapp.rh.role.Role;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Employé de l'entreprise (table « employes »). */
@Entity
@Table(name = "employes")
@Getter @Setter @NoArgsConstructor
public class Employe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "date_embauche", nullable = false)
    private LocalDate dateEmbauche;

    @Column(precision = 10, scale = 2)
    private BigDecimal salaire;

    // LAZY : le département n'est chargé depuis la base que si on l'utilise
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departement_id")
    private Departement departement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private Role role;

    // Le manager est lui-même un employé (relation vers la même table)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employe manager;
}