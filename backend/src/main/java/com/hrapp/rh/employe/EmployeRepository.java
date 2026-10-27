package com.hrapp.rh.employe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeRepository extends JpaRepository<Employe, Long> {

    boolean existsByRoleId(Long roleId);

    long countByDepartementId(Long departementId);

    List<Employe> findByDepartementId(Long departementId);
}