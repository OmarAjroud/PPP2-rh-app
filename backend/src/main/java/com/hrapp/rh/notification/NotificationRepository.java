package com.hrapp.rh.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByDestinataireId(Long destinataireId, Pageable pageable);

    Page<Notification> findByDestinataireIdAndLueFalse(Long destinataireId, Pageable pageable);

    /** Compteur de la cloche : appelé régulièrement par le front-end, il reste léger (index dédié). */
    long countByDestinataireIdAndLueFalse(Long destinataireId);

    Optional<Notification> findByIdAndDestinataireId(Long id, Long destinataireId);

    /** Marque toutes mes notifications non lues comme lues ; renvoie le nombre de lignes modifiées. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.lue = true, n.dateLecture = :maintenant "
            + "where n.destinataire.id = :destinataireId and n.lue = false")
    int markAllRead(@Param("destinataireId") Long destinataireId, @Param("maintenant") LocalDateTime maintenant);
}
