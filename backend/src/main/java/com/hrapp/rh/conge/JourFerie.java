package com.hrapp.rh.conge;

import com.hrapp.rh.audit.Audited;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Jour férié (non décompté des congés). */
@Audited
@Entity
@Table(name = "jours_feries")
@Getter @Setter @NoArgsConstructor
public class JourFerie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_ferie", nullable = false, unique = true)
    private LocalDate dateFerie;

    @Column(nullable = false, length = 100)
    private String libelle;
}