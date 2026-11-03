package com.hrapp.rh.conge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TypeCongeRepository extends JpaRepository<TypeConge, Long> {

    List<TypeConge> findAllByOrderByLibelle();

    /** Types qui consomment un solde annuel. */
    List<TypeConge> findByDeduitDuSoldeTrueOrderByLibelle();
}