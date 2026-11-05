package com.hrapp.rh.tableaudebord;

import com.hrapp.rh.conge.StatutDemandeConge;
import com.hrapp.rh.employe.Employe;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Requêtes d'agrégation du tableau de bord : les calculs (comptes, sommes, regroupements) sont faits par la base.
 * Partout, departementId = null signifie « toute l'entreprise ».
 */
public interface TableauDeBordRepository extends Repository<Employe, Long> {

    // ---------- indicateurs ----------

    @Query("select count(e) from Employe e where (:dep is null or e.departement.id = :dep)")
    long countEmployees(@Param("dep") Long departementId);

    @Query("select count(e) from Employe e where (:dep is null or e.departement.id = :dep) "
            + "and e.dateEmbauche between :debut and :fin")
    long countHires(@Param("dep") Long departementId, @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("select count(d) from DemandeConge d where d.statut = :statut "
            + "and (:dep is null or d.employe.departement.id = :dep)")
    long countRequests(@Param("dep") Long departementId, @Param("statut") StatutDemandeConge statut);

    /** Employés distincts en congé approuvé ce jour-là. */
    @Query("select count(distinct d.employe.id) from DemandeConge d where d.statut = :statut "
            + "and :jour between d.dateDebut and d.dateFin "
            + "and (:dep is null or d.employe.departement.id = :dep)")
    long countAbsent(@Param("dep") Long departementId, @Param("jour") LocalDate jour,
                     @Param("statut") StatutDemandeConge statut);

    // ---------- graphiques ----------

    /** Tous les départements (même vides), du plus grand au plus petit. */
    @Query("""
            select new com.hrapp.rh.tableaudebord.TableauDeBordResponse$EffectifDepartement(
                d.id, d.nom, count(e))
            from Departement d left join Employe e on e.departement = d
            where (:dep is null or d.id = :dep)
            group by d.id, d.nom
            order by count(e) desc, d.nom
            """)
    List<TableauDeBordResponse.EffectifDepartement> headcountByDepartment(@Param("dep") Long departementId);

    /** [mois (1-12), nombre] pour les seuls mois ayant au moins une embauche. */
    @Query("""
            select extract(month from e.dateEmbauche), count(e)
            from Employe e
            where e.dateEmbauche between :debut and :fin and (:dep is null or e.departement.id = :dep)
            group by extract(month from e.dateEmbauche)
            """)
    List<Object[]> hiresByMonth(@Param("dep") Long departementId, @Param("debut") LocalDate debut,
                                @Param("fin") LocalDate fin);

    @Query("""
            select new com.hrapp.rh.tableaudebord.TableauDeBordResponse$RepartitionConge(
                t.code, t.libelle, count(d), sum(d.nombreJours))
            from DemandeConge d join d.typeConge t
            where d.statut = :statut and d.dateDebut between :debut and :fin
              and (:dep is null or d.employe.departement.id = :dep)
            group by t.id, t.code, t.libelle
            order by sum(d.nombreJours) desc
            """)
    List<TableauDeBordResponse.RepartitionConge> leaveByType(@Param("dep") Long departementId,
                                                             @Param("statut") StatutDemandeConge statut,
                                                             @Param("debut") LocalDate debut,
                                                             @Param("fin") LocalDate fin);

    /** [departementId, nom, nombre d'employés, total des salaires, nombre de salaires renseignés]. */
    @Query("""
            select d.id, d.nom, count(e), sum(e.salaire), count(e.salaire)
            from Departement d left join Employe e on e.departement = d
            where (:dep is null or d.id = :dep)
            group by d.id, d.nom
            order by d.nom
            """)
    List<Object[]> payrollByDepartment(@Param("dep") Long departementId);

    @Query("select count(d) > 0 from Departement d where d.id = :dep")
    boolean departmentExists(@Param("dep") Long departementId);
}
