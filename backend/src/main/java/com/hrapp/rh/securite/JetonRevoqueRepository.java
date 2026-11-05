package com.hrapp.rh.securite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface JetonRevoqueRepository extends JpaRepository<JetonRevoque, String> {

    /** Les jetons expirés n'ont plus besoin d'être mémorisés : ils sont refusés de toute façon. */
    @Modifying
    @Query("delete from JetonRevoque j where j.dateExpiration < :maintenant")
    int deleteExpired(@Param("maintenant") LocalDateTime maintenant);
}
