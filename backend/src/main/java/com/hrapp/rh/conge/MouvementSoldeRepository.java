package com.hrapp.rh.conge;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MouvementSoldeRepository extends JpaRepository<MouvementSolde, Long> {

    /** Historique d'un employé pour une année, regroupé par solde puis chronologique (sert au calcul du solde cumulé). */
    @EntityGraph(attributePaths = {"solde", "solde.typeConge", "creePar"})
    @Query("select m from MouvementSolde m "
            + "where m.solde.employe.id = :employeId and m.solde.annee = :annee "
            + "order by m.solde.id, m.dateMouvement, m.id")
    List<MouvementSolde> findHistory(@Param("employeId") Long employeId, @Param("annee") int annee);
}