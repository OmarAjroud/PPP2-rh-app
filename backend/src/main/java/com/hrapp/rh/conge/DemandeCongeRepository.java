package com.hrapp.rh.conge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;

public interface DemandeCongeRepository
        extends JpaRepository<DemandeConge, Long>, JpaSpecificationExecutor<DemandeConge> {

    /** Recherche paginée avec chargement de l'employé, du type et du décideur en une seule requête. */
    @Override
    @EntityGraph(attributePaths = {"employe", "typeConge", "decidePar"})
    Page<DemandeConge> findAll(Specification<DemandeConge> spec, Pageable pageable);

    /**
     * Chevauchement : une demande existante chevauche la période demandée
     * si elle commence avant (ou le jour de) la fin demandée ET finit après (ou le jour du) début demandé.
     */
    boolean existsByEmployeIdAndStatutInAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
            Long employeId, Collection<StatutDemandeConge> statuts, LocalDate fin, LocalDate debut);

    /** Total de jours des demandes d'un employé, pour un type, un statut et une période (null s'il n'y en a pas). */
    @Query("select sum(d.nombreJours) from DemandeConge d "
            + "where d.employe.id = :employeId and d.typeConge.id = :typeId and d.statut = :statut "
            + "and d.dateDebut >= :debut and d.dateDebut <= :fin")
    BigDecimal sumJours(@Param("employeId") Long employeId, @Param("typeId") Long typeId,
                        @Param("statut") StatutDemandeConge statut,
                        @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
}