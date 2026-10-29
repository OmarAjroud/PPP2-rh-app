package com.hrapp.rh.utilisateur;

import com.hrapp.rh.employe.Employe;
import jakarta.persistence.*;
import lombok.*;

/** Compte de connexion lié à un employé (table « utilisateurs »). */
@Entity
@Table(name = "utilisateurs")
@Getter @Setter @NoArgsConstructor
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employe_id", nullable = false, unique = true)
    private Employe employe;

    @Column(name = "nom_utilisateur", nullable = false, unique = true, length = 100)
    private String nomUtilisateur;

    @Column(name = "mot_de_passe_hash", nullable = false)
    private String motDePasseHash;

    @Column(nullable = false)
    private boolean actif = true;
}