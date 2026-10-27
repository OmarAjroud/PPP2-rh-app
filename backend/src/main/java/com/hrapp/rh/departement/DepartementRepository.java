package com.hrapp.rh.departement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DepartementRepository extends JpaRepository<Departement, Long> {

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);

    @Query("""
            select new com.hrapp.rh.departement.DepartementResponse(
                d.id, d.nom, d.description, count(e))
            from Departement d
            left join Employe e on e.departement = d
            group by d.id, d.nom, d.description
            order by d.nom
            """)
    List<DepartementResponse> listerAvecNombreEmployes();
}