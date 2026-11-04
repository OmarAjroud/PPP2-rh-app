package com.hrapp.rh.conge;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SoldeCongeRepository extends JpaRepository<SoldeConge, Long> {

    Optional<SoldeConge> findByEmployeIdAndTypeCongeIdAndAnnee(Long employeId, Long typeCongeId, int annee);

    /** Même recherche, avec verrou : personne d'autre ne modifie ce solde avant la fin de la transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SoldeConge s "
            + "where s.employe.id = :employeId and s.typeConge.id = :typeId and s.annee = :annee")
    Optional<SoldeConge> findForUpdate(@Param("employeId") Long employeId, @Param("typeId") Long typeId,
                                       @Param("annee") int annee);
}