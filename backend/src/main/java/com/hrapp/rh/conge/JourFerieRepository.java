package com.hrapp.rh.conge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface JourFerieRepository extends JpaRepository<JourFerie, Long> {

    List<JourFerie> findByDateFerieBetweenOrderByDateFerie(LocalDate debut, LocalDate fin);

    boolean existsByDateFerie(LocalDate dateFerie);
}