package com.hrapp.rh.utilisateur;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    @EntityGraph(attributePaths = {"employe", "employe.role", "employe.departement"})
    Optional<Utilisateur> findByNomUtilisateur(String nomUtilisateur);

    @EntityGraph(attributePaths = {"employe", "employe.role"})
    Optional<Utilisateur> findByEmployeId(Long employeId);

    @EntityGraph(attributePaths = {"employe", "employe.role"})
    Optional<Utilisateur> findByEmployeEmailIgnoreCase(String email);

    boolean existsByNomUtilisateur(String nomUtilisateur);

    void deleteByEmployeId(Long employeId);
}