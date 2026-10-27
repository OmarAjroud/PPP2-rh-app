package com.hrapp.rh.role;

import jakarta.persistence.*;
import lombok.*;

/** Rôle applicatif : ADMIN, RH, MANAGER, EMPLOYE (table « roles »). */
@Entity
@Table(name = "roles")
@Getter @Setter @NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String nom;

    @Column(length = 255)
    private String description;
}