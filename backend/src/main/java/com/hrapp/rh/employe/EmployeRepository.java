package com.hrapp.rh.employe;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeRepository
        extends JpaRepository<Employe, Long>, JpaSpecificationExecutor<Employe> {


    /** Verrouille la ligne de l'employé jusqu'à la fin de la transaction (sérialise ses demandes de congé). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employe e where e.id = :id")
    Optional<Employe> findByIdForUpdate(@Param("id") Long id);

    // Utilisées par RoleService et DepartementService (étape 2)
    boolean existsByRoleId(Long roleId);

    long countByDepartementId(Long departementId);

    List<Employe> findByDepartementId(Long departementId);

    // Nouvelles méthodes
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByManagerId(Long managerId);

    /** Recherche paginée avec chargement du département et du rôle en une seule requête. */
    @Override
    @EntityGraph(attributePaths = {"departement", "role"})
    Page<Employe> findAll(Specification<Employe> spec, Pageable pageable);

    Optional<Employe> findByEmailIgnoreCase(String email);

    /** Employés ayant un rôle donné (ex. tous les RH, destinataires des demandes sans manager). */
    List<Employe> findByRoleNom(String nomRole);

    long countByRoleNom(String nomRole);
}