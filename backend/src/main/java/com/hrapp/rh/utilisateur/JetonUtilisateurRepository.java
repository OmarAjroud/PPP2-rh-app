package com.hrapp.rh.utilisateur;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface JetonUtilisateurRepository extends JpaRepository<JetonUtilisateur, Long> {

    @EntityGraph(attributePaths = "utilisateur")
    Optional<JetonUtilisateur> findByJetonHash(String jetonHash);

    boolean existsByUtilisateurIdAndTypeJetonAndDateCreationAfter(Long utilisateurId, TypeJeton typeJeton,
                                                                  LocalDateTime depuis);

    /** Marque le jeton comme utilisé, seulement s'il ne l'était pas déjà. Renvoie 1 si c'est réussi, 0 sinon. */
    @Modifying(flushAutomatically = true)
    @Query("update JetonUtilisateur j set j.dateUtilisation = :maintenant "
            + "where j.id = :id and j.dateUtilisation is null")
    int markUsed(@Param("id") Long id, @Param("maintenant") LocalDateTime maintenant);

    /** Invalide les jetons non utilisés d'un utilisateur pour un type donné. */
    @Modifying(flushAutomatically = true)
    @Query("update JetonUtilisateur j set j.dateUtilisation = :maintenant "
            + "where j.utilisateur.id = :utilisateurId and j.typeJeton = :typeJeton and j.dateUtilisation is null")
    int invalidateUnused(@Param("utilisateurId") Long utilisateurId, @Param("typeJeton") TypeJeton typeJeton,
                         @Param("maintenant") LocalDateTime maintenant);

    /** Invalide tous les jetons non utilisés d'un utilisateur (tous types). */
    @Modifying(flushAutomatically = true)
    @Query("update JetonUtilisateur j set j.dateUtilisation = :maintenant "
            + "where j.utilisateur.id = :utilisateurId and j.dateUtilisation is null")
    int invalidateAllUnused(@Param("utilisateurId") Long utilisateurId,
                            @Param("maintenant") LocalDateTime maintenant);
}