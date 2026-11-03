package com.hrapp.rh.conge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SoldeCongeRepository extends JpaRepository<SoldeConge, Long> {

    Optional<SoldeConge> findByEmployeIdAndTypeCongeIdAndAnnee(Long employeId, Long typeCongeId, int annee);
}