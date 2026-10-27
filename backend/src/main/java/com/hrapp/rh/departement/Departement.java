package com.hrapp.rh.departement;

import jakarta.persistence.*;
import lombok.*;

/** Département de l'entreprise (table « departements »). */
@Entity
@Table(name = "departements")
@Getter @Setter @NoArgsConstructor
public class Departement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // AUTO_INCREMENT MySQL
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nom;

    @Column(length = 255)
    private String description;
}